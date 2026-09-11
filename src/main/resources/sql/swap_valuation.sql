-- Fachliche Logik analog zum Excel-Sheet 2_Marktdaten_Mapping.
-- forward_rates.rate entspricht dort "Forward / Referenzzins".
-- discount_rates.rate entspricht dort "Discount Rate".

WITH enriched AS (
    SELECT
        c.trade_id,
        c.period,
        c.cashflow_date,
        c.currency,
        c.nominal,
        c.fixed_rate,
        c.accrual_years,
        f.rate AS forward_rate,
        d.rate AS discount_rate,
        -c.nominal * c.fixed_rate * c.accrual_years AS payer_cashflow,
        c.nominal * f.rate * c.accrual_years AS receiver_cashflow
    FROM swap_cashflows c
    JOIN forward_rates f
      ON f.rate_date = c.cashflow_date
    JOIN discount_rates d
      ON d.rate_date = c.cashflow_date
)
SELECT
    trade_id,
    period,
    cashflow_date,
    currency,
    nominal,
    forward_rate AS rate,
    discount_rate AS discount,
    (
        payer_cashflow + receiver_cashflow
    ) / POWER(
        1 + discount_rate * accrual_years,
        period
    ) AS present_value
FROM enriched;
