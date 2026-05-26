package com.lightschedule.modules.writeback;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WritebackAuditMapper extends BaseMapper<WritebackAuditEntity> {
}
