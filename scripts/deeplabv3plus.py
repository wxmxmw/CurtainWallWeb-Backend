import torch
import torch.nn as nn
import torch.nn.functional as F
from mobilenetv2 import mobilenetv2


# --- ASPP 模块 (保持标准配置) ---
class _DenseASPPConv(nn.Sequential):
    def __init__(
        self,
        in_channels,
        inter_channels,
        out_channels,
        atrous_rate,
        drop_rate=0.1,
        norm_layer=nn.BatchNorm2d,
        norm_kwargs=None,
    ):
        super(_DenseASPPConv, self).__init__()
        self.add_module("conv1", nn.Conv2d(in_channels, inter_channels, 1)),
        self.add_module(
            "bn1",
            norm_layer(inter_channels, **({} if norm_kwargs is None else norm_kwargs)),
        ),
        self.add_module("relu1", nn.ReLU(True)),
        self.add_module(
            "conv2",
            nn.Conv2d(
                inter_channels,
                out_channels,
                3,
                dilation=atrous_rate,
                padding=atrous_rate,
            ),
        ),
        self.add_module(
            "bn2",
            norm_layer(out_channels, **({} if norm_kwargs is None else norm_kwargs)),
        ),
        self.add_module("relu2", nn.ReLU(True)),
        self.drop_rate = drop_rate

    def forward(self, x):
        features = super(_DenseASPPConv, self).forward(x)
        if self.drop_rate > 0:
            features = F.dropout(features, p=self.drop_rate, training=self.training)
        return features


class _DenseASPPBlock(nn.Module):
    def __init__(
        self,
        in_channels,
        inter_channels1,
        inter_channels2,
        norm_layer=nn.BatchNorm2d,
        norm_kwargs=None,
    ):
        super(_DenseASPPBlock, self).__init__()
        self.aspp_6 = _DenseASPPConv(
            in_channels,
            inter_channels1,
            inter_channels2,
            6,
            0.1,
            norm_layer,
            norm_kwargs,
        )
        self.aspp_12 = _DenseASPPConv(
            in_channels + inter_channels2 * 1,
            inter_channels1,
            inter_channels2,
            12,
            0.1,
            norm_layer,
            norm_kwargs,
        )
        self.aspp_18 = _DenseASPPConv(
            in_channels + inter_channels2 * 2,
            inter_channels1,
            inter_channels2,
            18,
            0.1,
            norm_layer,
            norm_kwargs,
        )
        self.branch5_conv = nn.Conv2d(
            in_channels + inter_channels2 * 3, inter_channels2, 1, 1, 0, bias=True
        )
        self.branch5_bn = nn.BatchNorm2d(inter_channels2)
        self.branch5_relu = nn.ReLU(inplace=True)

    def forward(self, x):
        [b, c, row, col] = x.size()
        aspp6 = self.aspp_6(x)
        x = torch.cat([aspp6, x], dim=1)
        aspp12 = self.aspp_12(x)
        x = torch.cat([aspp12, x], dim=1)
        aspp18 = self.aspp_18(x)
        x = torch.cat([aspp18, x], dim=1)

        global_feature = torch.mean(x, 2, True)
        global_feature = torch.mean(global_feature, 3, True)
        global_feature = self.branch5_conv(global_feature)
        global_feature = self.branch5_bn(global_feature)
        global_feature = self.branch5_relu(global_feature)
        global_feature = F.interpolate(
            global_feature, (row, col), None, "bilinear", True
        )
        x = torch.cat([global_feature, x], dim=1)
        return x


# --- 标准 DeepLabV3+ (MobileNetV2) ---
class DeepLab(nn.Module):
    def __init__(self, num_classes, backbone="mobilenet", pretrained=True):
        super(DeepLab, self).__init__()

        # 1. Backbone
        self.backbone = mobilenetv2(pretrained)

        # MobileNetV2 的最终输出层是 1280 通道
        in_channels = 1280
        # 浅层特征 (24通道)
        low_level_channels = 24

        # 2. DenseASPP (头部)
        self.denseaspp = _DenseASPPBlock(in_channels, 512, 256)

        # 计算 ASPP 输出通道数: 1280 + 256*4 = 2304
        dense_out_channels = in_channels + 256 * 4

        # 3. Project (2304 -> 256) - 恢复标准宽度
        self.project = nn.Sequential(
            nn.Conv2d(dense_out_channels, 256, 1, bias=False),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),
        )

        # 4. Shortcut (24 -> 48)
        self.shortcut_conv = nn.Sequential(
            nn.Conv2d(low_level_channels, 48, 1),
            nn.BatchNorm2d(48),
            nn.ReLU(inplace=True),
        )

        # 5. Decoder (256+48=304 -> 256 -> 256)
        self.cat_conv = nn.Sequential(
            nn.Conv2d(304, 256, 3, stride=1, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.Dropout(0.1),
            nn.Conv2d(256, 256, 3, stride=1, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.Dropout(0.1),
        )

        self.cls_conv = nn.Conv2d(256, num_classes, 1, stride=1)

    def forward(self, x):
        # 提取特征
        # MobileNetV2 的 features 是个 Sequential
        # 0-3 层是浅层特征
        low_level_features = self.backbone.features[:4](x)
        # 4-18 层是深层特征
        x = self.backbone.features[4:](low_level_features)

        # ASPP
        x = self.denseaspp(x)
        x = self.project(x)

        # Decoder
        low_level_features = self.shortcut_conv(low_level_features)

        # 4倍上采样
        x = F.interpolate(
            x, size=low_level_features.size()[2:], mode="bilinear", align_corners=True
        )

        x = torch.cat((x, low_level_features), dim=1)
        x = self.cat_conv(x)
        x = self.cls_conv(x)

        # 4倍上采样回原图
        x = F.interpolate(x, scale_factor=4, mode="bilinear", align_corners=True)

        return x
