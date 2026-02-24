-- Seed: Cotton Land STT vocabulary hints
-- Terms are hinted to Google STT only after the pet reaches min_level.

INSERT INTO aimon.world_vocabulary (world_code, term, min_level) VALUES
-- Level 1: global game terms (always active)
('COTTON_LAND', 'AIMON', 1),

-- Level 3: Baby stage — Sweet Dominion intro
('COTTON_LAND', 'Coneko', 3),
('COTTON_LAND', 'Kẻ Vô Vị', 3),
('COTTON_LAND', 'Tasteless', 3),
('COTTON_LAND', 'Liên Minh', 3),
('COTTON_LAND', 'Cảnh Ngọt', 3),
('COTTON_LAND', 'Xứ Bông', 3),
('COTTON_LAND', 'Marshmallow Meadow', 3),
('COTTON_LAND', 'Candy Lantern Town', 3),

-- Level 5: Child — world expands
('COTTON_LAND', 'Pretzel Pier', 5),
('COTTON_LAND', 'Citrus Arch', 5),
('COTTON_LAND', 'Cayenne Roadrunner', 5),
('COTTON_LAND', 'Rừng Chua', 5),
('COTTON_LAND', 'Cảng Mặn Mòi', 5),
('COTTON_LAND', 'Hoang Mạc Cay', 5),
('COTTON_LAND', 'Vanilla Promenade', 5),
('COTTON_LAND', 'Citrus Canopy', 5),
('COTTON_LAND', 'Soda Springs', 5),

-- Level 6: Child — landmarks unlocked
('COTTON_LAND', 'Whipcream Spire', 6),
('COTTON_LAND', 'Soda Bloom', 6),
('COTTON_LAND', 'Brinewind Lighthouse', 6),
('COTTON_LAND', 'Saltglass Market', 6),
('COTTON_LAND', 'Biscuit Hills', 6),
('COTTON_LAND', 'Lũng Đắng', 6),

-- Level 7: first cracks
('COTTON_LAND', 'Mocha Gate', 7),
('COTTON_LAND', 'Limewatch Lookout', 7),
('COTTON_LAND', 'Chim Ớt Đưa Thư', 7),

-- Level 8: deeper world
('COTTON_LAND', 'Bitter Hollow', 8),
('COTTON_LAND', 'Noir Coneko', 8),
('COTTON_LAND', 'Sugarcore', 8),
('COTTON_LAND', 'Bitter Mist', 8),
('COTTON_LAND', 'Berrytrack Ridge', 8),

-- Level 9: truth layer
('COTTON_LAND', 'Flavorcore', 9),
('COTTON_LAND', 'Ngũ Vị', 9),
('COTTON_LAND', 'Kỷ Ngũ Vị Cộng Hưởng', 9),
('COTTON_LAND', 'Umami Trench', 9),

-- Level 10: adult — ruins + hidden lore
('COTTON_LAND', 'Ashcaramel Ruins', 10),
('COTTON_LAND', 'Tirakuma', 10),
('COTTON_LAND', 'Red Dune Runway', 10),
('COTTON_LAND', 'Chili Chameleon', 10)

ON CONFLICT (world_code, term) DO NOTHING;
