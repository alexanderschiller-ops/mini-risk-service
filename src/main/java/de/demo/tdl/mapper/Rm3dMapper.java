package de.demo.tdl.mapper;

import de.demo.tdl.domain.RiskPosition;
import de.demo.tdl.domain.Rm3dRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface Rm3dMapper {

    @Mapping(target = "tradeId", source = "tradeId")
    @Mapping(target = "marketValue", source = "presentValue")
    Rm3dRecord toRm3d(RiskPosition source);
}
