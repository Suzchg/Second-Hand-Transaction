-- After-Sale v2: 添加运费归属字段
-- 运行前请确认 after_sale_requests 表名与实际一致

ALTER TABLE after_sale_requests
  ADD COLUMN IF NOT EXISTS shipping_paid_by VARCHAR(16) COMMENT '运费承担方：BUYER/SELLER/PLATFORM',
  ADD COLUMN IF NOT EXISTS shipping_cost_cent INT COMMENT '运费金额（分）';
