package de.demo.tdl.mapper;

import de.demo.tdl.domain.EnrichedSwapCashflow;
import de.demo.tdl.domain.RiskPosition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RiskPositionMapper {

    @Mapping(
            target = "positionId",
            expression = "java(String.format(\"POS-%s-%02d\", source.tradeId(), source.period()))")
    @Mapping(target = "productType", constant = "Plain Vanilla Swap")
    @Mapping(target = "rate", source = "forwardRate")
    @Mapping(target = "discount", source = "discountRate")
    RiskPosition toRiskPosition(EnrichedSwapCashflow source);
}
