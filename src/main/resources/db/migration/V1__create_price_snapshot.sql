CREATE TABLE price_snapshot
(
    id                     BIGSERIAL PRIMARY KEY,
    symbol                 VARCHAR(20) NOT NULL,
    price                  NUMERIC     NOT NULL,
    close_time             TIMESTAMPTZ NOT NULL,
    average_window_minutes INTEGER     NOT NULL,
    fetched_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_price_snapshot_symbol_close_time UNIQUE (symbol, close_time)
);

CREATE INDEX idx_price_snapshot_symbol_fetched_at ON price_snapshot (symbol, fetched_at);
