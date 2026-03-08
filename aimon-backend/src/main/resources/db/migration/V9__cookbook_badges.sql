-- Cookbook milestone badges: awarded when unique food count reaches thresholds
INSERT INTO badges (code, name, description, category, icon, condition_type, condition_config, xp_reward)
VALUES
  ('cookbook_explorer', 'Người Khám Phá', 'Khám phá 10 món ăn', 'cookbook', 'cookbook_explorer.png', 'COUNTER', '{"action":"unique_food","count":10}', 50),
  ('cookbook_apprentice', 'Đầu Bếp Nhí', 'Khám phá 25 món ăn', 'cookbook', 'cookbook_apprentice.png', 'COUNTER', '{"action":"unique_food","count":25}', 100),
  ('cookbook_chef', 'Bếp Trưởng', 'Khám phá 50 món ăn', 'cookbook', 'cookbook_chef.png', 'COUNTER', '{"action":"unique_food","count":50}', 200),
  ('cookbook_master', 'Bậc Thầy Ẩm Thực', 'Khám phá 100 món ăn', 'cookbook', 'cookbook_master.png', 'COUNTER', '{"action":"unique_food","count":100}', 500);
