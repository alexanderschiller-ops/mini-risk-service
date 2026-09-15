package de.demo.tdl.mapper;

import de.demo.tdl.domain.RiskPosition;
import de.demo.tdl.domain.Rm3dRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface Rm3dMapper {

    // @tdl.field-map job=risk_positions_to_rm3d source=risk_positions.trade_id target=rm3d.trade_id
    // @tdl.field-map job=risk_positions_to_rm3d source=risk_positions.present_value target=rm3d.market_value
    @Mapping(target = "marketValue", source = "presentValue")
    Rm3dRecord toRm3d(RiskPosition source);
}
