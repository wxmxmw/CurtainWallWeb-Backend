package com.company.curtainwall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.curtainwall.entity.SysAiModel;

import java.util.List;

public interface SysAiModelService extends IService<SysAiModel> {
    List<SysAiModel> getActiveModels();
}
