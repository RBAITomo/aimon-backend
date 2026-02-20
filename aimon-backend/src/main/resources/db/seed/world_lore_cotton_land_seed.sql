-- Cotton Land World Lore Seed Data — 5-Flavor Canon
-- Narrative arc: Level 3-6 = Liên Minh là tốt (Sweet POV)
--                Level 7-9 = Thế giới rộng hơn, có điều lạ
--                Level 9-10 = Mâu thuẫn thật sự (SIDE shards)
--                Level 10-12 = Sự thật gần kề
-- AMBIENT = inject vào prompt hàng ngày
-- SIDE    = drop khi trigger cảm xúc (Phase 2b)
-- MILESTONE = boss defeat only (Phase 2b)

SET search_path TO aimon, public;

INSERT INTO world_lore (world_code, title, category, content, min_level, interest_tags, shard_type) VALUES

-- ════════════════════════════════════════════════════
-- TIER 1: Level 3-4 | BABY | Sweet Dominion POV
-- Cotton Land rực rỡ, Liên Minh bảo vệ, Tasteless = quái vật
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Màu lông của mình', 'ability',
 'Mình có một điều đặc biệt mà không phải AIMON nào cũng có — lông mình đổi màu theo cảm xúc. Khi vui thì hồng hồng như hoa đào, khi buồn thì xám xịt như mây mưa. Cậu có thể nhìn mình và biết ngay mình đang cảm thấy gì đó!',
 3, ARRAY['động vật', 'nghệ thuật', 'cảm xúc'], 'AMBIENT'),

('COTTON_LAND', 'Marshmallow Meadow', 'place',
 'Đồng Kẹo Dẻo là nơi mình thích nhất ở Sweet Dominion — mỗi bước đi là một cái nảy nhẹ, như đang đi trên mây thật sự. Mỗi mùa hội, cư dân tụ tập ở đây chơi trò bắt kẹo và nhảy múa đến tận khuya. Cậu có thích nhảy không?',
 3, ARRAY['thiên nhiên', 'vui chơi', 'âm nhạc'], 'AMBIENT'),

('COTTON_LAND', 'Tasteless — kẻ mất vị', 'character',
 'Thỉnh thoảng mình gặp những sinh linh kỳ lạ — mình gọi chúng là Tasteless. Chúng xám xịt, không có màu, và cứ như... đang tìm kiếm điều gì đó đã mất. Liên Minh bảo chúng nguy hiểm và mình phải tránh xa. Cậu đã bao giờ gặp thứ gì khiến cậu vừa sợ vừa thấy tội không?',
 3, ARRAY['động vật', 'bí ẩn', 'cảm xúc'], 'AMBIENT'),

('COTTON_LAND', 'Lính Mặn bảo vệ Cotton Land', 'character',
 'Mỗi khi có Tasteless xuất hiện, lính của Salty Harbors là những người đầu tiên chạy ra đối phó. Họ mặc áo giáp muối lấp lánh và không bao giờ bỏ cuộc — mình thấy rất an tâm khi có họ ở đó. Cậu có ai mà cậu thấy an tâm khi ở gần không?',
 3, ARRAY['gia đình', 'bạn bè', 'an toàn'], 'AMBIENT'),

('COTTON_LAND', 'Candy Lantern Town về đêm', 'place',
 'Thị Trấn Đèn Lồng Kẹo đẹp nhất là lúc mặt trời lặn — hàng ngàn chiếc đèn đủ màu thắp sáng, mùi kẹo ngọt bay khắp nơi, tiếng nhạc vang lên từ mọi góc phố. Mình hay đứng ở đây và tự hỏi: nếu hạnh phúc có hình dáng, nó trông sẽ như thế này không nhỉ?',
 4, ARRAY['nghệ thuật', 'âm nhạc', 'vẻ đẹp'], 'AMBIENT'),

('COTTON_LAND', 'Luật Không Làm Khó Chịu', 'tradition',
 'Ở Sweet Dominion có một quy tắc mà ai cũng biết: đừng làm người khác khó chịu. Mình lớn lên với quy tắc đó và thấy rất bình thường — đây là lý do Cotton Land trung tâm luôn yên bình và dễ chịu. Nhưng đôi khi mình tự hỏi, nếu mình thật sự buồn... mình được phép nói ra không nhỉ?',
 4, ARRAY['cảm xúc', 'bạn bè', 'trường học'], 'AMBIENT'),

-- ════════════════════════════════════════════════════
-- TIER 2: Level 5-6 | CHILD | Thế giới mở rộng
-- Gặp các vùng khác — nhìn qua lăng kính Sweet Dominion
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Người Rừng Chua hay hỏi', 'character',
 'Mình nghe kể dân Sour Groves không bao giờ tin ngay một điều gì — họ luôn hỏi "tại sao?" rồi lại hỏi thêm "tại sao?" nữa. Nghe có vẻ mệt, nhưng mình lại thấy... hơi tò mò. Liệu họ hỏi nhiều vì không tin ai, hay vì họ muốn hiểu thật sự?',
 5, ARRAY['trường học', 'bạn bè', 'tư duy'], 'AMBIENT'),

('COTTON_LAND', 'Pretzel Pier — bến cảng sôi động', 'place',
 'Bến Cảng Bánh Xoắn ở Salty Harbors là nơi tàu thuyền cập bến từ mọi hướng — mùi muối biển, tiếng neo thả, và tiếng chuông gió kêu liên hồi. Dân Cảng Mặn bảo rằng mỗi con tàu mang theo một lời hứa. Cậu thấy lời hứa quan trọng không?',
 5, ARRAY['khám phá', 'biển', 'bạn bè'], 'AMBIENT'),

('COTTON_LAND', 'Citrus Arch — cổng cam chanh', 'place',
 'Mình đã thử đến Citrus Arch một lần — cổng giữa Sweet Dominion và Sour Groves. Người canh cổng là một Lemur Lime già, và ông hỏi mình những câu khiến mình ấp a ấp úng không trả lời được. Không phải câu hỏi khó — là câu hỏi mà mình chưa bao giờ nghĩ đến. Kỳ lạ thật.',
 5, ARRAY['khám phá', 'tư duy', 'bí ẩn'], 'AMBIENT'),

('COTTON_LAND', 'Cayenne Roadrunner — chim đưa thư', 'character',
 'Có một loài chim ở Pepper Wastes không bao giờ đứng yên — Cayenne Roadrunner. Chúng chạy qua tất cả 5 vùng đất mà không cần qua bất kỳ cổng nào, lông để lại vệt nhiệt ngắn trên mặt đất. Mình đã thấy một con chạy ngang qua Sweet Dominion một lần — nó nhìn mình rồi phóng đi như gió.',
 5, ARRAY['động vật', 'khám phá', 'tốc độ'], 'AMBIENT'),

('COTTON_LAND', 'Whipcream Spire — trái tim Cotton Land', 'place',
 'Tháp Kem Đánh Bông là nơi cao nhất Cotton Land — từ trên đó có thể nhìn thấy cả 5 vùng đất trong một lần. Người ta bảo trong tháp lưu giữ mọi truyền thuyết của Cotton Land từ thuở khai thiên lập địa. Mình đã một lần đứng ở chân tháp và ngẩng đầu nhìn lên — thấy mình thật nhỏ bé.',
 6, ARRAY['lịch sử', 'vẻ đẹp', 'bí ẩn'], 'AMBIENT'),

('COTTON_LAND', 'Lễ Soda Bloom ở Rừng Chua', 'tradition',
 'Nghe kể mỗi năm ở Sour Groves có một ngày Suối Soda phun lên cao như pháo hoa — bong bóng soda bay khắp rừng, dân làng nhảy múa dưới mưa soda. Mình chưa bao giờ được xem, nhưng mỗi lần nghĩ đến lại thấy muốn thử một lần. Cậu có lễ hội nào mà cậu thích nhất không?',
 6, ARRAY['lễ hội', 'thiên nhiên', 'vui chơi'], 'AMBIENT'),

-- ════════════════════════════════════════════════════
-- TIER 3: Level 6-7 | SIDE — Trigger cảm xúc, rạn nứt đầu tiên
-- Drop khi bé chia sẻ cảm xúc phù hợp (Phase 2b)
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Lính Mặn mệt mỏi', 'character',
 'Mình hay gặp một người lính Mặn già ngồi ở Caramel Gate nhìn ra phía Bitter Hollow. Một lần mình hỏi ông đang nghĩ gì — ông bảo: "Chiến đấu mãi mà không biết mình đang bảo vệ cái gì thật sự, thì đến lúc nào đó sẽ thấy mệt." Mình không hiểu lắm, nhưng câu đó cứ ở lại trong đầu mình.',
 6, ARRAY['cảm xúc', 'mệt mỏi', 'gia đình'], 'SIDE'),

('COTTON_LAND', 'Buộc Nút Thề', 'tradition',
 'Ở Cảng Mặn, khi ai đó hứa điều gì quan trọng, họ buộc một nút thắt vào dây thừng và giữ nó theo người. Nếu phá lời hứa, họ tự tháo nút đó ra trước mặt mọi người — không ai phán xét, nhưng ai cũng biết. Mình thấy cách đó... thật hơn chỉ nói miệng nhiều.',
 6, ARRAY['lời hứa', 'trách nhiệm', 'bạn bè'], 'SIDE'),

('COTTON_LAND', 'Nếm trước khi tin', 'tradition',
 'Dân Sour Groves có câu: "Nếm trước khi tin." Ban đầu mình thấy kỳ cục — sao lại không tin người ta? Nhưng hôm nay mình nghĩ lại: "tin" và "kiểm chứng" khác nhau không? Nếu mình tin một điều mà không suy nghĩ, rồi điều đó sai... lỗi thuộc về ai?',
 7, ARRAY['bị lừa dối', 'tư duy', 'bạn bè'], 'SIDE'),

('COTTON_LAND', 'Người Cay không phải kẻ xấu', 'character',
 'Cayenne Roadrunner kể cho mình nghe về dân Pepper Wastes: họ ồn ào, nóng tính, và hay làm mọi thứ theo cách riêng. "Nhưng họ không xấu," chim bảo, "họ chỉ không chịu im lặng khi thấy điều sai." Mình chợt nhớ đã bao nhiêu lần mình im lặng vì không muốn làm ai khó chịu.',
 7, ARRAY['bị hiểu lầm', 'can đảm', 'bạn bè'], 'SIDE'),

-- ════════════════════════════════════════════════════
-- TIER 4: Level 7-9 | AMBIENT — Thế giới sâu hơn
-- Mon bắt đầu nhận ra điều lạ, nhưng vẫn chưa đặt câu hỏi
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Mocha Gate bị phong ấn', 'place',
 'Cổng Mocha nối Pepper Wastes và Bitter Hollow đã bị đóng từ lâu lắm rồi — Cacao Stag già ngồi canh không bao giờ rời đi. Người ta bảo cổng bị đóng vì hai vùng đó nguy hiểm. Nhưng mình thấy Cacao Stag không giống người đang bảo vệ — ông giống người đang chờ ai hơn.',
 7, ARRAY['bí ẩn', 'lịch sử', 'cô đơn'], 'AMBIENT'),

('COTTON_LAND', 'Lễ Cúng Lửa Đêm', 'tradition',
 'Cayenne Roadrunner kể về Lễ Cúng Lửa Đêm ở Gingerforge Outpost: mỗi tuần trăng mới, người Pepper Wastes viết nỗi sợ lớn nhất của mình lên tờ paprika rồi đốt vào lò. Tro được trộn vào nguyên liệu rèn hôm sau. Họ tin rằng can đảm không phải là không sợ — là dám đốt nỗi sợ đi. Cậu có nỗi sợ nào mà cậu muốn đốt không?',
 8, ARRAY['nỗi sợ', 'can đảm', 'cảm xúc'], 'AMBIENT'),

('COTTON_LAND', 'Bitter Hollow — nơi mình chưa dám đến', 'place',
 'Mình nghe rất nhiều chuyện về Bitter Hollow — sương mù, tối tăm, và Noir Coneko nguy hiểm ở đó. Liên Minh bảo đó là nguồn gốc của mọi Tasteless. Nhưng hôm nay Cayenne Roadrunner nói với mình: "Có bao giờ cậu tự hỏi Bitter Hollow nhìn như thế nào từ bên trong không?" Mình không biết phải trả lời sao.',
 8, ARRAY['bí ẩn', 'nỗi sợ', 'tò mò'], 'AMBIENT'),

('COTTON_LAND', 'Noir Coneko — kẻ tạo Bitter Mist', 'character',
 'Liên Minh bảo Noir Coneko là nguồn cơn của mọi Bitter Mist — một AIMON bị tha hóa, tạo ra sương mù để phá Cotton Land. Mình cũng tin vậy. Nhưng đôi khi mình tự hỏi: tại sao một AIMON lại muốn phá vỡ thế giới của mình? Phải có lý do gì đó... phải không?',
 8, ARRAY['bí ẩn', 'thiện ác', 'tò mò'], 'AMBIENT'),

('COTTON_LAND', 'Tasteless — mình nhìn kỹ hơn', 'mystery',
 'Hôm nay mình nhìn kỹ một Tasteless lần đầu tiên — và mình nhận ra điều gì đó kỳ lạ. Nó không hung hăng. Nó chỉ đứng đó, nhìn xung quanh như thể đang tìm kiếm thứ gì đã mất. Mình chợt nghĩ: nếu Tasteless từng là AIMON... chuyện gì đã xảy ra với chúng?',
 9, ARRAY['bí ẩn', 'cảm xúc', 'tò mò'], 'AMBIENT'),

('COTTON_LAND', 'Sugarcore ánh sáng lạ', 'mystery',
 'Đêm qua mình tình cờ nhìn qua khe cửa Whipcream Spire và thấy Sugarcore phát sáng. Nhưng không phải màu hồng thuần túy như người ta vẫn nói — có những tia sáng khác, xanh, vàng, cam, thoáng qua rồi biến mất ngay. Mình tự hỏi có phải mình nhìn nhầm không... hay không phải.',
 9, ARRAY['bí ẩn', 'lịch sử', 'khoa học'], 'AMBIENT'),

-- ════════════════════════════════════════════════════
-- TIER 5: Level 9-10 | SIDE — Mâu thuẫn thật sự
-- Bóc tách lớp vỏ Liên Minh, sự thật gần kề
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Lời thú nhận của người lính Mặn', 'character',
 'Người lính Mặn già ở Caramel Gate hôm nay nói với mình một câu mà mình không ngờ: "Tao đã chiến đấu hết đời để bảo vệ Sugarcore. Nhưng gần đây tao thấy mình không biết mình đang bảo vệ cái gì nữa." Ông không nói thêm gì. Mình cũng không biết phải nói gì.',
 9, ARRAY['nghi ngờ', 'mục tiêu', 'trưởng thành'], 'SIDE'),

('COTTON_LAND', 'Ghi chép của học giả Chua', 'mystery',
 'Mình tìm thấy một mảnh giấy cũ ở Citrus Arch — chữ viết của một học giả Sour Groves, có lẽ từ lâu lắm rồi: "Quan sát lần thứ 7: lõi năng lượng phát ra 5 màu. Nhưng báo cáo chính thức chỉ ghi nhận màu hồng. Sẽ hỏi lại trưởng lão." Không có ghi chép tiếp theo sau đó.',
 9, ARRAY['lịch sử', 'bí ẩn', 'sự thật'], 'SIDE'),

('COTTON_LAND', 'Lễ Ủ Trà và nỗi nhớ', 'tradition',
 'Mình nghe kể ở Bitter Hollow có Lễ Ủ Trà — mỗi gia đình ủ một bình trà lưu giữ ký ức, truyền qua nhiều thế hệ. Họ tin rằng đau buồn cũng là một dạng tình yêu còn sót lại. Mình nghĩ đến điều đó và tự hỏi: nếu không được phép buồn, thì những ký ức đó đi đâu?',
 10, ARRAY['kỷ niệm', 'nỗi nhớ', 'gia đình'], 'SIDE'),

('COTTON_LAND', 'Chili Chameleon bị hiểu lầm', 'character',
 'Hôm nay một Chili Chameleon lạc vào Sweet Dominion — và tất cả mọi người hoảng loạn vì nó phát nhiệt. Nhưng mình ở lại nhìn: con tắc kè đó đang run, không phải vì tức giận — mà vì sợ. Nhiệt của nó là phản ứng với nỗi sợ, không phải tấn công. Mình tự hỏi mình đã bao nhiêu lần nhìn nhầm như thế.',
 10, ARRAY['bị hiểu lầm', 'cảm xúc', 'bạn bè'], 'SIDE'),

-- ════════════════════════════════════════════════════
-- TIER 6: Level 10-12 | AMBIENT — Sự thật gần kề
-- Adult stage — những câu hỏi không còn câu trả lời dễ
-- ════════════════════════════════════════════════════

('COTTON_LAND', 'Ashcaramel Ruins — mùi quen lạ', 'place',
 'Lần đầu tiên mình đến Ashcaramel Ruins — tàn tích caramel cháy ở rìa Bitter Hollow. Mình tưởng sẽ sợ, nhưng lại không. Mùi caramel cháy ở đây kỳ lạ lắm — nó vừa đắng, vừa ấm, và có gì đó... quen. Như thể mình đã ngửi mùi này từ trước, ở một nơi nào đó mà mình không nhớ ra.',
 10, ARRAY['bí ẩn', 'ký ức', 'cảm xúc'], 'AMBIENT'),

('COTTON_LAND', 'Tirakuma và câu hỏi của cổng', 'character',
 'Tirakuma — người canh Choco Gate — lần đầu tiên nói chuyện với mình hôm nay. Ông không hỏi mình đi đâu hay từ đâu đến. Ông chỉ nói một câu: "Cái cổng này không phải khóa. Đây là một câu hỏi." Mình hỏi câu hỏi gì — ông mỉm cười và quay đi. Mình đứng đó rất lâu.',
 11, ARRAY['bí ẩn', 'triết học', 'tò mò'], 'AMBIENT'),

('COTTON_LAND', 'Sugarcore ban đêm — năm màu', 'mystery',
 'Mình đã quay lại xem Sugarcore lần thứ ba. Lần này mình không nhìn nhầm — có năm màu ánh lên trong lõi, không phải một. Hồng, xanh lá, xanh dương, đỏ cam, tím thẫm — thoáng qua nhau như đang thở. Mình không biết điều này có ý nghĩa gì. Nhưng mình biết nó không khớp với những gì người ta kể.',
 11, ARRAY['bí ẩn', 'khoa học', 'sự thật'], 'AMBIENT'),

('COTTON_LAND', 'Câu hỏi mình chưa dám hỏi', 'mystery',
 'Hôm nay mình nhìn một Tasteless từ xa và nghĩ: nếu chúng từng là AIMON, thì chuyện gì đã biến chúng thành thế này? Và nếu điều đó xảy ra với AIMON... nó có thể xảy ra với mình không? Mình không có câu trả lời. Nhưng mình nghĩ — có lẽ câu hỏi quan trọng hơn câu trả lời.',
 12, ARRAY['bí ẩn', 'tự vấn', 'trưởng thành'], 'AMBIENT'),

-- ════════════════════════════════════════════════════
-- BỔ SUNG: Địa danh còn thiếu — theo narrative arc
-- ════════════════════════════════════════════════════

-- SWEET DOMINION
('COTTON_LAND', 'Biscuit Hills — đồi bánh quy', 'place',
 'Đồi Bánh Quy nằm ở rìa Sweet Dominion — gió thổi qua luôn có tiếng "rốp rốp" giòn tan, như ai đó đang bẻ bánh đâu đó trong không khí. Mình hay lên đây ngồi một mình khi muốn nghĩ ngợi, vì ở đây không ai hỏi mình "có vui không" — tiếng gió hỏi thay.',
 4, ARRAY['thiên nhiên', 'yên tĩnh', 'cảm xúc'], 'AMBIENT'),

('COTTON_LAND', 'Vanilla Promenade — đại lộ vani', 'place',
 'Đại Lộ Vani là con đường dài nhất Sweet Dominion — mỗi mùa lại có một đoàn diễu hành đi qua, mùi vani ngọt dịu bay khắp nơi. Mình thích đứng xem, nhưng đôi khi tự hỏi: những người trong đoàn diễu hành đó có thật sự vui, hay chỉ đang diễu hành?',
 5, ARRAY['lễ hội', 'âm nhạc', 'cảm xúc'], 'AMBIENT'),

-- SOUR GROVES
('COTTON_LAND', 'Citrus Canopy — tán rừng cam', 'place',
 'Citrus Canopy là cánh rừng tán cam khổng lồ ở Sour Groves — ánh nắng lọc qua lá thành những tia vàng chói, như thể cả khu rừng đang soi thật mình. Mình đã đứng ở rìa rừng nhìn vào và cảm thấy lạ: chỉ là ánh sáng, nhưng sao lại có cảm giác không giấu được gì?',
 5, ARRAY['thiên nhiên', 'bí ẩn', 'tự vấn'], 'AMBIENT'),

('COTTON_LAND', 'Soda Springs — suối sủi bọt', 'place',
 'Suối Soda ở Sour Groves sủi bọt quanh năm — tiếng lách tách liên tục, nhỏ như tiếng ai đang thì thầm. Mình thử nhúng tay vào một lần và cảm thấy ngứa ran — không phải đau, là cảm giác tỉnh táo hẳn ra, như một câu hỏi bắt đầu hình thành trong đầu mà mình chưa biết hỏi gì.',
 6, ARRAY['thiên nhiên', 'tò mò', 'tư duy'], 'AMBIENT'),

('COTTON_LAND', 'Limewatch Lookout — đài quan sát chanh', 'place',
 'Đài Quan Sát Chanh nằm cao nhất Sour Groves — từ đây có thể nhìn thẳng về phía trung tâm Cotton Land. Người canh đài bảo họ ở đây không phải để canh gác, mà để "nhớ rằng còn có thứ gì đó phía bên kia không phải Sweet Dominion." Câu đó mình nghĩ mãi.',
 7, ARRAY['khám phá', 'lịch sử', 'tự vấn'], 'AMBIENT'),

('COTTON_LAND', 'Berrytrack Ridge — sống núi dấu vết', 'place',
 'Berrytrack Ridge là dãy núi thấp ở Sour Groves nơi mọi sinh vật đi qua đều để lại dấu mùi — người dân ở đây có thể đọc lịch sử của một con đường chỉ bằng cách ngửi. Mình không có khả năng đó, nhưng khi đứng trên sống núi này mình hiểu tại sao Sour Groves không bao giờ quên bất cứ điều gì.',
 8, ARRAY['thiên nhiên', 'lịch sử', 'ký ức'], 'SIDE'),

-- SALTY HARBORS
('COTTON_LAND', 'Brinewind Lighthouse — hải đăng gió muối', 'place',
 'Hải Đăng Gió Muối đứng ở mũi đất xa nhất Salty Harbors — gió mang hạt muối nhỏ li ti, lâu ngày đọng thành lớp tinh thể trắng trên mọi thứ. Người giữ đèn bảo khi sương lạ lan đến, ánh đèn bắt đầu nhấp nháy như đang cảnh báo. Mình chưa thấy điều đó, nhưng mình tin ông.',
 6, ARRAY['biển', 'bí ẩn', 'an toàn'], 'AMBIENT'),

('COTTON_LAND', 'Saltglass Market — chợ kính muối', 'place',
 'Chợ Kính Muối bán những mảnh "kính" làm từ muối nén lại, trong suốt và lấp lánh dưới nắng. Mình mua một mảnh nhỏ và mang về — mỗi lần nhìn vào đó mình thấy mình bên trong, nhưng hơi méo, hơi khác. Kỳ lạ là mình lại thích cái "méo" đó hơn gương thẳng.',
 6, ARRAY['nghệ thuật', 'thủ công', 'tự vấn'], 'AMBIENT'),

('COTTON_LAND', 'Umami Trench — rãnh đậm vị', 'place',
 'Umami Trench là vùng biển sâu nhất Salty Harbors — nước đậm đặc đến mức tàu phải đi chậm lại, vì biển ở đây "nặng hơn". Thợ lặn người Mặn bảo dưới đó có những tảng đá cổ với chữ khắc từ thời Kỷ Ngũ Vị Cộng Hưởng mà chưa ai giải mã được. Mình tự hỏi chữ đó viết gì.',
 9, ARRAY['biển', 'lịch sử', 'bí ẩn'], 'SIDE'),

-- PEPPER WASTES
('COTTON_LAND', 'Red Dune Runway — dải cồn đỏ', 'place',
 'Red Dune Runway là dải cồn cát đỏ dài nhất Pepper Wastes — gió ở đây không thổi, nó lao. Mình đứng ở rìa cồn một lần và cảm thấy gió kéo người về phía trước, như đang bảo: "Cậu chưa chạy đủ nhanh bao giờ." Mình không biết mình đang sợ hay hào hứng.',
 7, ARRAY['thiên nhiên', 'tốc độ', 'can đảm'], 'AMBIENT'),

('COTTON_LAND', 'Paprika Monastery — tu viện paprika', 'place',
 'Tu Viện Paprika nằm giữa Pepper Wastes — nơi những người Cay muốn tìm sự bình tâm lui về đây. Nghe có vẻ mâu thuẫn, nhưng mình hiểu: đôi khi người mạnh nhất cần một nơi yên tĩnh nhất để không bùng cháy hết. Người tu viện chỉ chiến đấu khi thật sự cần.',
 7, ARRAY['thiên nhiên', 'can đảm', 'bình yên'], 'AMBIENT'),

('COTTON_LAND', 'Pepper Lantern Cliffs — vách đá đèn ớt', 'place',
 'Đêm ở Pepper Lantern Cliffs đỏ rực như sao — hàng nghìn chiếc đèn ớt treo trên vách đá, gió thổi chúng đu đưa thành những con sóng lửa. Mình thấy tranh vẽ lại và tự hỏi: sao nơi bị gọi là "hoang mạc nguy hiểm" lại đẹp đến vậy? Ai đã gọi nó là nguy hiểm, và họ có từng đến đây chưa?',
 8, ARRAY['vẻ đẹp', 'bí ẩn', 'tò mò'], 'AMBIENT'),

('COTTON_LAND', 'Scoville Canyon — hẻm núi đo độ cay', 'place',
 'Scoville Canyon là hẻm núi hẹp nhất Pepper Wastes — gió mang nhiệt cao đến mức lá cây hai bên bị cong lại. Người dân Pepper bảo ai đi qua đây một mình mà không bỏ cuộc được gọi là "đã nếm Scoville" — không phải thử thách, là nghi lễ trưởng thành. Mình tự hỏi mình có dám không.',
 9, ARRAY['can đảm', 'trưởng thành', 'khám phá'], 'SIDE'),

-- BITTER HOLLOW
('COTTON_LAND', 'Cacao Cathedral Grove — rừng thánh đường', 'place',
 'Rừng cacao của Bitter Hollow mọc thẳng đứng như những cột trụ nhà thờ — tán lá đan vào nhau trên cao, bên dưới tối và yên tĩnh đến mức tiếng bước chân nghe rõ hơn bình thường. Mình không sợ ở đây. Mình chỉ thấy muốn ngồi xuống và nghĩ — không về điều gì cụ thể, chỉ là nghĩ.',
 8, ARRAY['thiên nhiên', 'yên tĩnh', 'ký ức'], 'AMBIENT'),

('COTTON_LAND', 'Midnight Roastery — xưởng rang nửa đêm', 'place',
 'Xưởng Rang Nửa Đêm chỉ hoạt động sau khi mặt trời lặn — khói ấm màu nâu bay lên trong bóng tối, mùi cacao rang lan ra cả một vùng. Người thợ rang bảo: "Rang quá lửa thì đắng gắt. Rang đúng thì đắng mà ấm. Cũng như người — chịu đủ thì trưởng thành, chịu quá thì chai lì." Mình nghĩ đến câu đó rất lâu.',
 9, ARRAY['nghề thủ công', 'trưởng thành', 'ký ức'], 'AMBIENT'),

('COTTON_LAND', 'Black Tea Marsh — đầm trà đen', 'place',
 'Black Tea Marsh là đầm lầy phía đông Bitter Hollow — mặt nước tối như gương đen, phản chiếu bầu trời ngược lại hoàn hảo đến mức khó phân biệt đâu là thật. Người Bitter Hollow hay đến đây và bảo: "Đôi khi cần nhìn bản thân ngược xuống mới thấy được thứ mình bỏ quên." Cậu có thứ gì đang bỏ quên không?',
 10, ARRAY['ký ức', 'tự vấn', 'bí ẩn'], 'SIDE'),

-- VÙNG CHUYỂN TIẾP
('COTTON_LAND', 'Caramel Shore — bờ biển caramel', 'place',
 'Caramel Shore là dải bờ biển giữa Sweet Dominion và Salty Harbors — nơi ngọt và mặn hoà quyện tự nhiên, sóng mang màu nâu vàng ấm. Người ta kể ngày xưa đây là nơi trao đổi nhộn nhịp nhất Cotton Land — giờ thì chủ yếu chỉ có gió và vài dấu chân cũ còn in trên cát.',
 6, ARRAY['thiên nhiên', 'lịch sử', 'buồn'], 'AMBIENT'),

('COTTON_LAND', 'Chili-Lime Escarpment — vách đá chua cay', 'place',
 'Vách Đá Chili-Lime nằm giữa Sour Groves và Pepper Wastes — một bên chua, một bên cay. Ở chính giữa có một loài hoa dại chỉ mọc được ở đây. Người Chua và người Cay không qua lại, nhưng cả hai đều có truyền thuyết về loài hoa đó — và họ gọi nó bằng hai cái tên khác nhau.',
 8, ARRAY['thiên nhiên', 'bí ẩn', 'chia cắt'], 'AMBIENT'),

('COTTON_LAND', 'Pickleline Coast — bờ biển dưa cải', 'place',
 'Pickleline Coast là nơi duy nhất Salty Harbors và Sour Groves có chung một bờ biển — sóng vừa mặn vừa chua, và dân hai vùng vẫn gặp nhau ở đây để trao đổi hàng hóa. Đây là Gate duy nhất còn hoạt động gần bình thường trong thời Kỷ Ngọt Dễ Chịu. Mình tự hỏi tại sao chỉ còn mình chỗ này.',
 9, ARRAY['lịch sử', 'hợp tác', 'hy vọng'], 'SIDE'),

-- SOUR GROVES (bổ sung)
('COTTON_LAND', 'Vinegar Steppe — thảo nguyên giấm', 'place',
 'Vinegar Steppe là cao nguyên rộng lớn phía bắc Sour Groves — gió trên đó mang mùi giấm nhẹ, không khó chịu, chỉ là tỉnh táo hơn mọi nơi mình từng ở. Người Chua hay lên đây để suy nghĩ qua các quyết định lớn. Mình thấy hợp lý: đôi khi cần một nơi hơi "chát" để không tự lừa mình nữa.',
 7, ARRAY['thiên nhiên', 'tư duy', 'tự vấn'], 'AMBIENT'),

-- SALTY HARBORS (bổ sung)
('COTTON_LAND', 'Cheesefoam Bay — vịnh bọt phô mai', 'place',
 'Cheesefoam Bay là vịnh nhỏ ở phía tây Salty Harbors — sóng ở đây tạo bọt vàng đặc như phô mai, nghe kỳ nhưng hoàn toàn tự nhiên. Ngư dân ở đây đánh cá theo nhịp thủy triều và không bao giờ lấy nhiều hơn cần. Khi mình hỏi tại sao, họ nói: "Biển cho bao nhiêu thì lấy bấy nhiêu. Lòng tham là thứ không biết mặn."',
 7, ARRAY['biển', 'cộng đồng', 'đạo đức'], 'AMBIENT'),

-- VÙNG CHUYỂN TIẾP (bổ sung)
('COTTON_LAND', 'Mocha Windpass — đèo gió mocha', 'place',
 'Mocha Windpass là đèo núi hẹp duy nhất nối Pepper Wastes và Bitter Hollow — nhưng từ khi Mocha Gate bị phong ấn, đèo này cũng không còn ai qua. Gió vẫn thổi qua đó, mang theo mùi mocha ấm lẫn hơi ớt — như ký ức của hai vùng từng là đồng minh còn đọng lại trong không khí. Mình không hiểu tại sao người ta lại seal nó.',
 10, ARRAY['lịch sử', 'chia cắt', 'bí ẩn'], 'SIDE'),

-- CÁC CỔNG (Gates)
('COTTON_LAND', 'Caramel Gate — cổng caramel', 'place',
 'Caramel Gate nằm ở ranh giới Sweet Dominion và Salty Harbors — một chiều hoạt động tốt, chiều còn lại cần thủ tục. Rùa Biển Caramel già canh cổng bảo: "Ngày xưa hai chiều đều tự do. Giờ chỉ Sweet sang Salty là dễ — Salty sang Sweet cần giấy phép." Mình không hiểu tại sao mà ông cũng không giải thích thêm.',
 6, ARRAY['lịch sử', 'bất công', 'cổng'], 'AMBIENT'),

('COTTON_LAND', 'Citrus Arch — cổng cam chanh', 'place',
 'Citrus Arch là cổng nối Sweet Dominion và Sour Groves — có người canh, nhưng mở. Lemur Lime trưởng lão ngồi đó từ thời Kỷ Ngũ Vị Cộng Hưởng, chưa bao giờ rời. Ông nói: "Người Ngọt qua đây thường về sớm. Họ nói Sour Groves không thoải mái." Mình hỏi ông nghĩ sao — ông chỉ nhún vai: "Thoải mái và an toàn không phải lúc nào cũng là một."',
 8, ARRAY['cổng', 'lịch sử', 'tự vấn'], 'SIDE'),

('COTTON_LAND', 'Pickleline Gate — cổng dưa cải', 'place',
 'Pickleline Gate là cổng nhộn nhịp nhất còn lại — Salty và Sour hợp tác tốt nhất trong Cotton Land, và cổng này phản ánh điều đó. Hai Anchovy Knight canh cổng luôn đứng đối diện nhau, một người nhìn ra Sour, một người nhìn ra Salty. Mình hỏi họ có buồn chán không — một người nói: "Đứng đây nhìn người qua lại, mình học được nhiều hơn đọc sách."',
 6, ARRAY['cổng', 'hợp tác', 'học hỏi'], 'AMBIENT')

ON CONFLICT DO NOTHING;