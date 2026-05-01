import argparse
import json
import os
import sys
import base64
import cv2
import numpy as np
import time
import torch
import torch.nn.functional as F
from torchvision import transforms

# 尝试导入 YOLO
try:
    from ultralytics import YOLO
except ImportError:
    YOLO = None

# 尝试导入 DeepLab (假设 deeplabv3plus.py 在同一目录下或 PYTHONPATH 中)
# 你需要确保 deeplabv3plus.py 在 scripts 目录或能被引用到
try:
    from deeplabv3plus import DeepLab
except ImportError:
    DeepLab = None


def _get_model(model_path, model_type):
    if model_type == "deeplab":
        if DeepLab is None:
            raise ImportError("DeepLab module missing")
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        model = DeepLab(num_classes=2, backbone="mobilenet", pretrained=False)
        checkpoint = torch.load(model_path, map_location=device)
        state_dict = checkpoint["model"] if "model" in checkpoint else checkpoint
        new_state_dict = {k.replace("_orig_mod.", ""): v for k, v in state_dict.items()}
        model.load_state_dict(new_state_dict, strict=False)
        model.to(device)
        model.eval()
        return model
    else:
        # YOLO
        if YOLO is None:
            raise ImportError("Ultralytics YOLO module missing")
        return YOLO(model_path)


def _compute_yolo_stats(result, shape):
    h, w = shape
    boxes = result.boxes
    count = 0
    area_ratio = 0.0
    avg_conf = 0.0

    if boxes is not None and boxes.xyxy is not None:
        xyxy = boxes.xyxy.cpu().numpy()
        count = len(xyxy)
        if count > 0:
            masks_obj = getattr(result, "masks", None)
            if masks_obj is not None and hasattr(masks_obj, "data"):
                masks_data = masks_obj.data.cpu().numpy()
                if masks_data.ndim == 3 and masks_data.shape[0] > 0:
                    mask_union = np.any(masks_data > 0.5, axis=0).astype(np.uint8)
                    if mask_union.shape != (h, w):
                        mask_union = cv2.resize(
                            mask_union, (w, h), interpolation=cv2.INTER_NEAREST
                        )
                    area_ratio = np.sum(mask_union) / (h * w)
            else:
                mask = np.zeros((h, w), dtype=np.uint8)
                for x1, y1, x2, y2 in xyxy:
                    x1, y1 = max(0, int(x1)), max(0, int(y1))
                    x2, y2 = min(w, int(x2)), min(h, int(y2))
                    mask[y1:y2, x1:x2] = 1
                area_ratio = np.sum(mask) / (h * w)

            confs = boxes.conf.cpu().numpy() if boxes.conf is not None else np.array([])
            avg_conf = float(confs.mean()) if confs.size > 0 else 0.0

    return count, area_ratio, avg_conf


def run_inference(image_path, model_path, output_path, conf, iou, imgsz, max_det):
    # 1. 读取图片
    img_bgr = cv2.imread(image_path)
    if img_bgr is None:
        raise ValueError(f"Could not read image: {image_path}")
    h, w = img_bgr.shape[:2]

    # 2. 判断模型类型
    model_type = "deeplab" if model_path.endswith(".pth") else "yolo"
    model = _get_model(model_path, model_type)

    stats = {"count": 0, "area_ratio": 0.0, "avg_conf": 0.0}
    annotated_bgr = None

    # 3. 推理逻辑
    if model_type == "deeplab":
        device = next(model.parameters()).device
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        img_resized = cv2.resize(img_rgb, (256, 256))

        transform = transforms.Compose(
            [
                transforms.ToTensor(),
                transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
            ]
        )
        input_tensor = transform(img_resized).unsqueeze(0).to(device)

        with torch.no_grad():
            output = model(input_tensor)
            probs = F.softmax(output, dim=1)
            rust_prob_map = probs[0, 1, :, :].cpu().numpy()

        prob_map_full = cv2.resize(
            rust_prob_map, (w, h), interpolation=cv2.INTER_LINEAR
        )
        prob_uint8 = (prob_map_full * 255).astype(np.uint8)

        mean_prob = np.mean(prob_map_full)
        if mean_prob > 0.4:
            _, mask_binary = cv2.threshold(
                prob_uint8, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
            )
        else:
            _, mask_binary = cv2.threshold(
                prob_uint8, int(conf * 255), 255, cv2.THRESH_BINARY
            )

        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        mask_clean = cv2.morphologyEx(mask_binary, cv2.MORPH_OPEN, kernel, iterations=1)

        annotated_bgr = img_bgr.copy()
        contours, _ = cv2.findContours(
            mask_clean, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )

        rust_blob_count = 0
        rust_pixel_sum = 0
        conf_sum = 0.0

        for cnt in contours:
            if cv2.contourArea(cnt) < 50:
                continue
            rust_blob_count += 1
            rust_pixel_sum += cv2.contourArea(cnt)

            bx, by, bw, bh = cv2.boundingRect(cnt)
            roi_prob = prob_map_full[by : by + bh, bx : bx + bw]
            roi_mask = mask_clean[by : by + bh, bx : bx + bw]
            if np.sum(roi_mask) > 0:
                blob_conf = float(np.mean(roi_prob[roi_mask > 0]))
            else:
                blob_conf = 0.0
            conf_sum += blob_conf

            cv2.drawContours(annotated_bgr, [cnt], -1, (0, 255, 0), 2)

        if rust_blob_count > 0:
            color_mask = np.zeros_like(img_bgr)
            color_mask[mask_clean > 0] = [0, 0, 255]
            annotated_bgr = cv2.addWeighted(annotated_bgr, 1.0, color_mask, 0.35, 0)

        stats["count"] = rust_blob_count
        stats["area_ratio"] = float(rust_pixel_sum / (h * w))
        stats["avg_conf"] = (
            float(conf_sum / rust_blob_count) if rust_blob_count > 0 else 0.0
        )

    else:
        # YOLO
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        results = model.predict(
            source=img_rgb,
            conf=conf,
            iou=iou,
            imgsz=imgsz,
            max_det=max_det,
            verbose=False,
        )
        res = results[0]

        # Check if classification result
        if hasattr(res, "probs") and res.probs is not None:
            # === Classification Logic ===
            # 1. Extract Classification Info
            class_names = model.names
            top1_index = int(res.probs.top1)
            top1_name = class_names[top1_index]
            top1_conf = float(res.probs.top1conf.item())

            # 2. Run Segmentation (rust_seg_v2) for visual result
            seg_model_path = os.path.join(os.getcwd(), "runs", "rust_seg_v2", "weights", "best.pt")
            if not os.path.exists(seg_model_path):
                # Fallback: try relative to script if cwd is wrong
                script_dir = os.path.dirname(os.path.abspath(__file__))
                seg_model_path = os.path.join(script_dir, "..", "runs", "rust_seg_v2", "weights", "best.pt")
            
            if not os.path.exists(seg_model_path):
                print(f"Warning: Segmentation model not found at {seg_model_path}, using classification plot", file=sys.stderr)
                # res.plot() returns RGB because input was RGB
                annotated_rgb = res.plot()
                annotated_bgr = cv2.cvtColor(annotated_rgb, cv2.COLOR_RGB2BGR)
                # Dummy stats
                stats["count"] = 0
                stats["area_ratio"] = 0.0
                stats["avg_conf"] = top1_conf
            else:
                seg_model = YOLO(seg_model_path)
                seg_results = seg_model.predict(
                    source=img_rgb,
                    conf=conf,
                    iou=iou,
                    imgsz=imgsz,
                    max_det=max_det,
                    verbose=False
                )
                seg_res = seg_results[0]
                
                # seg_res.plot() returns RGB because input was RGB
                annotated_rgb = seg_res.plot()
                annotated_bgr = cv2.cvtColor(annotated_rgb, cv2.COLOR_RGB2BGR)
                
                count, area, avg = _compute_yolo_stats(seg_res, (h, w))
                stats["count"] = count
                stats["area_ratio"] = area
                stats["avg_conf"] = avg

            # Add classification info to stats
            stats["classification"] = {
                "label": top1_name,
                "confidence": top1_conf
            }

        else:
            # === Detection/Segmentation Logic ===
            # res.plot() returns the image in the same channel format as input source (RGB in our case)
            annotated_rgb = res.plot()
            # Convert RGB back to BGR for cv2.imwrite
            annotated_bgr = cv2.cvtColor(annotated_rgb, cv2.COLOR_RGB2BGR)

            count, area, avg = _compute_yolo_stats(res, (h, w))
            stats["count"] = count
            stats["area_ratio"] = area
            stats["avg_conf"] = avg

    # 4. 保存结果图
    cv2.imwrite(output_path, annotated_bgr)

    # 5. 输出 JSON 结果
    result = {"success": True, "metrics": stats, "output_path": output_path}
    print(json.dumps(result))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--iou", type=float, default=0.45)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--max_det", type=int, default=300)

    args = parser.parse_args()

    try:
        run_inference(
            args.image,
            args.model,
            args.output,
            args.conf,
            args.iou,
            args.imgsz,
            args.max_det,
        )
    except Exception as e:
        err = {"success": False, "error": str(e)}
        print(json.dumps(err))
        sys.exit(1)
