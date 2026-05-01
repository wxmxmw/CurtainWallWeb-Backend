package com.company.curtainwall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.curtainwall.entity.BizTaskBatch;
import com.company.curtainwall.mapper.BizTaskBatchMapper;
import com.company.curtainwall.service.BizTaskBatchService;
import org.springframework.stereotype.Service;

@Service
public class BizTaskBatchServiceImpl extends ServiceImpl<BizTaskBatchMapper, BizTaskBatch>
        implements BizTaskBatchService {
}