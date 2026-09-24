-- Demo input schema. No tables or existing data are replaced.
-- Unconstrained numeric preserves the decimal precision of the source data.
CREATE TABLE IF NOT EXISTS public.swap_cashflows (
    trade_id varchar(64) NOT NULL,
    period integer NOT NULL,
    cashflow_date date NOT NULL,
    currency varchar(3) NOT NULL,
    nominal numeric NOT NULL,
    fixed_rate numeric NOT NULL,
    accrual_years numeric NOT NULL,
    expected_payer_cashflow numeric NOT NULL,
    expected_receiver_cashflow numeric NOT NULL,
    expected_net_cashflow numeric NOT NULL,
    PRIMARY KEY (trade_id, period)
);

CREATE TABLE IF NOT EXISTS public.forward_rates (
    curve varchar(128) NOT NULL,
    rate_date date NOT NULL,
    rate numeric NOT NULL,
    PRIMARY KEY (curve, rate_date)
);

CREATE TABLE IF NOT EXISTS public.discount_rates (
    curve varchar(128) NOT NULL,
    rate_date date NOT NULL,
    rate numeric NOT NULL,
    PRIMARY KEY (curve, rate_date)
);
