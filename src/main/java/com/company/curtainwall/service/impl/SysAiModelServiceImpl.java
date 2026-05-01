package com.company.curtainwall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.curtainwall.entity.SysAiModel;
import com.company.curtainwall.mapper.SysAiModelMapper;
import com.company.curtainwall.service.SysAiModelService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysAiModelServiceImpl extends ServiceImpl<SysAiModelMapper, SysAiModel> implements SysAiModelService {

    @Override
    public List<SysAiModel> getActiveModels() {
        return this.list(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getIsActive, true));
    }
}
