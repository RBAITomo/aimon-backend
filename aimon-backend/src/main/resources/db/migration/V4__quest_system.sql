-- V4__quest_system.sql
-- Quest notification system: question bank + user quest history

SET search_path TO aimon, public;

-- Questions bank (seeded from JSON, rarely changes)
CREATE TABLE IF NOT EXISTS quest_questions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,
    category VARCHAR(30) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    question_text TEXT NOT NULL,
    expected_answer TEXT NOT NULL,
    hint TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Per-user quest assignment + answer history
CREATE TABLE IF NOT EXISTS quest_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES pet_profiles(user_id),
    question_id INT NOT NULL REFERENCES quest_questions(id),
    assigned_at TIMESTAMPTZ DEFAULT now(),
    answered_at TIMESTAMPTZ,
    is_correct BOOLEAN,
    attempt_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Partial index for fast pending quest lookup
CREATE INDEX idx_quest_history_user_pending
    ON quest_history(user_id, answered_at)
    WHERE answered_at IS NULL;

-- Ensure only one pending quest per user (prevents race condition)
CREATE UNIQUE INDEX idx_quest_one_pending
    ON quest_history(user_id)
    WHERE answered_at IS NULL;

-- Seed question bank
INSERT INTO quest_questions (code, category, difficulty, question_text, expected_answer, hint) VALUES
-- Math easy
('math_e_001', 'math', 'easy', '3 cộng 4 bằng mấy?', '7', 'Đếm trên ngón tay nha!'),
('math_e_002', 'math', 'easy', '5 cộng 2 bằng mấy?', '7', 'Đếm từ 5 thêm 2 nha!'),
('math_e_003', 'math', 'easy', '8 trừ 3 bằng mấy?', '5', 'Bớt đi 3 từ 8 nha!'),
('math_e_004', 'math', 'easy', '6 cộng 1 bằng mấy?', '7', 'Chỉ thêm 1 thôi!'),
('math_e_005', 'math', 'easy', '9 trừ 4 bằng mấy?', '5', 'Đếm ngược từ 9 nha!'),
('math_e_006', 'math', 'easy', '2 cộng 2 bằng mấy?', '4', 'Hai đôi là bao nhiêu?'),
('math_e_007', 'math', 'easy', '7 trừ 2 bằng mấy?', '5', 'Bớt 2 từ 7!'),
('math_e_008', 'math', 'easy', '1 cộng 8 bằng mấy?', '9', 'Thêm 1 vào 8!'),
('math_e_009', 'math', 'easy', '10 trừ 5 bằng mấy?', '5', 'Một nửa của 10!'),
('math_e_010', 'math', 'easy', '4 cộng 3 bằng mấy?', '7', 'Đếm thêm 3 từ 4!'),
('math_e_011', 'math', 'easy', '6 trừ 6 bằng mấy?', '0', 'Bớt hết thì còn gì?'),
-- Math medium
('math_m_001', 'math', 'medium', '12 cộng 15 bằng mấy?', '27', 'Cộng hàng đơn vị trước!'),
('math_m_002', 'math', 'medium', '3 nhân 4 bằng mấy?', '12', '3 lần 4 hoặc 4+4+4!'),
('math_m_003', 'math', 'medium', '20 trừ 8 bằng mấy?', '12', 'Từ 20 bớt 8!'),
('math_m_004', 'math', 'medium', '5 nhân 5 bằng mấy?', '25', '5 lần 5!'),
('math_m_005', 'math', 'medium', '36 chia 6 bằng mấy?', '6', '6 nhân mấy bằng 36?'),
('math_m_006', 'math', 'medium', '15 cộng 18 bằng mấy?', '33', '5+8=13, nhớ 1!'),
('math_m_007', 'math', 'medium', '7 nhân 3 bằng mấy?', '21', '7+7+7!'),
('math_m_008', 'math', 'medium', '40 trừ 17 bằng mấy?', '23', 'Mượn 1 từ hàng chục!'),
('math_m_009', 'math', 'medium', '8 nhân 2 bằng mấy?', '16', 'Gấp đôi 8!'),
('math_m_010', 'math', 'medium', '45 chia 9 bằng mấy?', '5', '9 nhân mấy bằng 45?'),
('math_m_011', 'math', 'medium', '50 trừ 25 bằng mấy?', '25', 'Một nửa của 50!'),
-- Math hard
('math_h_001', 'math', 'hard', '123 cộng 89 bằng mấy?', '212', 'Cộng từng hàng: đơn vị, chục, trăm!'),
('math_h_002', 'math', 'hard', '12 nhân 8 bằng mấy?', '96', '12 nhân 8 = 10x8 + 2x8!'),
('math_h_003', 'math', 'hard', '200 trừ 67 bằng mấy?', '133', 'Mượn từ hàng trăm!'),
('math_h_004', 'math', 'hard', '144 chia 12 bằng mấy?', '12', '12 nhân 12 bằng bao nhiêu?'),
('math_h_005', 'math', 'hard', 'Một hình vuông có cạnh 5cm, chu vi là bao nhiêu?', '20', 'Chu vi = 4 nhân cạnh!'),
('math_h_006', 'math', 'hard', '25 nhân 4 bằng mấy?', '100', '25 xu nhân 4 bằng 1 đồng!'),
('math_h_007', 'math', 'hard', '1000 trừ 456 bằng mấy?', '544', 'Mượn từ hàng nghìn!'),
('math_h_008', 'math', 'hard', 'Một tam giác có 3 cạnh bằng nhau, mỗi cạnh 7cm. Chu vi bằng mấy?', '21', '3 nhân 7!'),
('math_h_009', 'math', 'hard', '15 nhân 6 bằng mấy?', '90', '15x6 = 10x6 + 5x6!'),
('math_h_010', 'math', 'hard', 'Số nào nhân với chính nó bằng 81?', '9', 'Thử từ 1 đến 10!'),
('math_h_011', 'math', 'hard', '3 tá bút chì có bao nhiêu cái?', '36', '1 tá = 12!'),
-- Vietnamese easy
('viet_e_001', 'vietnamese', 'easy', 'Chữ cái đầu tiên trong bảng chữ cái tiếng Việt là gì?', 'A', 'Bắt đầu bằng nguyên âm!'),
('viet_e_002', 'vietnamese', 'easy', 'Con mèo kêu thế nào?', 'meo meo', 'Meo meo!'),
('viet_e_003', 'vietnamese', 'easy', 'Từ nào trái nghĩa với ''nóng''?', 'lạnh', 'Mùa đông thì...'),
('viet_e_004', 'vietnamese', 'easy', 'Hoàn thành câu: Trời mưa phải mang...?', 'ô', 'Để che mưa!'),
('viet_e_005', 'vietnamese', 'easy', 'Con gà trống kêu thế nào?', 'ò ó o', 'Sáng sớm nó gáy!'),
('viet_e_006', 'vietnamese', 'easy', 'Từ nào trái nghĩa với ''to''?', 'nhỏ', 'Bé xíu!'),
('viet_e_007', 'vietnamese', 'easy', 'Từ nào trái nghĩa với ''đẹp''?', 'xấu', 'Không đẹp thì là...'),
('viet_e_008', 'vietnamese', 'easy', 'Từ nào trái nghĩa với ''cao''?', 'thấp', 'Không cao thì...'),
('viet_e_009', 'vietnamese', 'easy', 'Con chó kêu thế nào?', 'gâu gâu', 'Nó sủa!'),
('viet_e_010', 'vietnamese', 'easy', 'Hoàn thành: Mẹ ơi, con yêu...?', 'mẹ', 'Yêu ai nhất?'),
('viet_e_011', 'vietnamese', 'easy', 'Từ nào trái nghĩa với ''dài''?', 'ngắn', 'Không dài thì...'),
-- Vietnamese medium
('viet_m_001', 'vietnamese', 'medium', 'Từ ''hạnh phúc'' có nghĩa là gì?', 'vui vẻ, sung sướng', 'Khi cảm thấy rất vui!'),
('viet_m_002', 'vietnamese', 'medium', 'Tìm từ đồng nghĩa với ''xinh đẹp''?', 'đẹp, xinh xắn', 'Khen ai trông dễ thương!'),
('viet_m_003', 'vietnamese', 'medium', 'Điền vào chỗ trống: ''Công cha như núi Thái Sơn, nghĩa mẹ như nước trong nguồn chảy...''?', 'ra', 'Nước chảy đi đâu?'),
('viet_m_004', 'vietnamese', 'medium', 'Từ ''kiên nhẫn'' có nghĩa là gì?', 'nhẫn nại, chịu đựng', 'Không bỏ cuộc!'),
('viet_m_005', 'vietnamese', 'medium', 'Hoàn thành: ''Một cây làm chẳng nên non, ba cây chụm lại nên hòn...''?', 'núi cao', 'Đoàn kết sức mạnh!'),
('viet_m_006', 'vietnamese', 'medium', 'Từ ''chăm chỉ'' trái nghĩa với từ gì?', 'lười biếng', 'Không muốn làm gì!'),
('viet_m_007', 'vietnamese', 'medium', 'Tìm từ đồng nghĩa với ''thông minh''?', 'sáng dạ, giỏi', 'Học giỏi, hiểu nhanh!'),
('viet_m_008', 'vietnamese', 'medium', 'Câu ''Ăn quả nhớ kẻ trồng cây'' nghĩa là gì?', 'biết ơn', 'Nhớ ơn người giúp mình!'),
('viet_m_009', 'vietnamese', 'medium', 'Từ ''dũng cảm'' có nghĩa là gì?', 'can đảm, không sợ', 'Gan dạ!'),
('viet_m_010', 'vietnamese', 'medium', 'Hoàn thành: ''Đi một ngày đàng, học một...''?', 'sàng khôn', 'Đi xa học được nhiều!'),
('viet_m_011', 'vietnamese', 'medium', 'Từ ''trung thực'' có nghĩa là gì?', 'thật thà, không nói dối', 'Luôn nói sự thật!'),
-- Vietnamese hard
('viet_h_001', 'vietnamese', 'hard', 'Ai là tác giả bài thơ ''Lượm''?', 'Tố Hữu', 'Nhà thơ cách mạng nổi tiếng!'),
('viet_h_002', 'vietnamese', 'hard', 'Truyện ''Tấm Cám'' thuộc thể loại gì?', 'cổ tích', 'Chuyện ngày xưa!'),
('viet_h_003', 'vietnamese', 'hard', 'Từ ''nhân hậu'' có nghĩa là gì?', 'tốt bụng, có lòng thương người', 'Hiền lành, tốt với mọi người!'),
('viet_h_004', 'vietnamese', 'hard', 'Câu ''Có công mài sắt có ngày nên kim'' dạy ta điều gì?', 'kiên trì, nhẫn nại', 'Làm mãi sẽ thành công!'),
('viet_h_005', 'vietnamese', 'hard', 'Ai viết ''Dế Mèn phiêu lưu ký''?', 'Tô Hoài', 'Nhà văn viết về thiên nhiên!'),
('viet_h_006', 'vietnamese', 'hard', 'Từ ''hào phóng'' trái nghĩa với từ gì?', 'keo kiệt', 'Không muốn chia sẻ!'),
('viet_h_007', 'vietnamese', 'hard', 'Câu ''Gần mực thì đen, gần đèn thì sáng'' khuyên ta điều gì?', 'chọn bạn tốt', 'Môi trường ảnh hưởng ta!'),
('viet_h_008', 'vietnamese', 'hard', 'Thể thơ lục bát có bao nhiêu chữ ở câu trên?', '6', 'Lục = 6!'),
('viet_h_009', 'vietnamese', 'hard', 'Truyện Kiều do ai viết?', 'Nguyễn Du', 'Đại thi hào dân tộc!'),
('viet_h_010', 'vietnamese', 'hard', 'Từ ''khiêm tốn'' có nghĩa là gì?', 'không khoe khoang, giản dị', 'Không tự cao!'),
('viet_h_011', 'vietnamese', 'hard', 'Hoàn thành: ''Bầu ơi thương lấy bí cùng, tuy rằng khác giống nhưng chung...''?', 'một giàn', 'Cùng một nơi!'),
-- General knowledge easy
('gen_e_001', 'general_knowledge', 'easy', 'Mặt trời mọc ở hướng nào?', 'đông', 'Hướng ngược với tây!'),
('gen_e_002', 'general_knowledge', 'easy', 'Một tuần có mấy ngày?', '7', 'Từ thứ 2 đến chủ nhật!'),
('gen_e_003', 'general_knowledge', 'easy', 'Lá cây có màu gì?', 'xanh', 'Màu của thiên nhiên!'),
('gen_e_004', 'general_knowledge', 'easy', 'Một năm có mấy tháng?', '12', 'Từ tháng 1 đến tháng...'),
('gen_e_005', 'general_knowledge', 'easy', 'Con cá sống ở đâu?', 'nước', 'Sông, biển, hồ!'),
('gen_e_006', 'general_knowledge', 'easy', 'Con voi có vòi dài hay ngắn?', 'dài', 'Rất dài!'),
('gen_e_007', 'general_knowledge', 'easy', 'Mưa rơi từ đâu xuống?', 'trời', 'Từ trên cao!'),
('gen_e_008', 'general_knowledge', 'easy', 'Con gì biết bay?', 'chim', 'Có cánh!'),
('gen_e_009', 'general_knowledge', 'easy', 'Quả chuối có màu gì khi chín?', 'vàng', 'Màu nắng!'),
('gen_e_010', 'general_knowledge', 'easy', 'Con mèo có mấy chân?', '4', 'Giống con chó!'),
('gen_e_011', 'general_knowledge', 'easy', 'Nước đá thì nóng hay lạnh?', 'lạnh', 'Rất lạnh!'),
-- General knowledge medium
('gen_m_001', 'general_knowledge', 'medium', 'Thủ đô của Việt Nam là gì?', 'Hà Nội', 'Ở miền Bắc!'),
('gen_m_002', 'general_knowledge', 'medium', 'Con vật nào là biểu tượng của sự chăm chỉ?', 'con ong', 'Bay từ hoa này sang hoa khác!'),
('gen_m_003', 'general_knowledge', 'medium', 'Trái đất quay quanh gì?', 'mặt trời', 'Ngôi sao gần nhất!'),
('gen_m_004', 'general_knowledge', 'medium', 'Nước sôi ở bao nhiêu độ C?', '100', 'Một trăm!'),
('gen_m_005', 'general_knowledge', 'medium', 'Con vật nào lớn nhất trên cạn?', 'voi', 'Ở châu Phi và châu Á!'),
('gen_m_006', 'general_knowledge', 'medium', 'Việt Nam có bao nhiêu tỉnh thành?', '63', 'Hơn 60!'),
('gen_m_007', 'general_knowledge', 'medium', 'Cầu vồng có mấy màu?', '7', 'Đỏ, cam, vàng...'),
('gen_m_008', 'general_knowledge', 'medium', 'Con gì chạy nhanh nhất thế giới?', 'báo gêpa', 'Sống ở châu Phi!'),
('gen_m_009', 'general_knowledge', 'medium', 'Hành tinh nào gần mặt trời nhất?', 'sao Thủy', 'Mercury!'),
('gen_m_010', 'general_knowledge', 'medium', 'Con vật nào ngủ đông?', 'gấu', 'Ngủ suốt mùa đông!'),
('gen_m_011', 'general_knowledge', 'medium', 'Kim cương cứng hơn hay sắt cứng hơn?', 'kim cương', 'Vật liệu cứng nhất!'),
-- General knowledge hard
('gen_h_001', 'general_knowledge', 'hard', 'Ai là vị vua đầu tiên của nhà Lý?', 'Lý Thái Tổ', 'Dời đô về Thăng Long!'),
('gen_h_002', 'general_knowledge', 'hard', 'Hồ lớn nhất Việt Nam là gì?', 'hồ Ba Bể', 'Ở tỉnh Bắc Kạn!'),
('gen_h_003', 'general_knowledge', 'hard', 'Sông nào dài nhất Việt Nam?', 'sông Mê Kông', 'Chảy qua nhiều nước!'),
('gen_h_004', 'general_knowledge', 'hard', 'Ai phát minh ra bóng đèn?', 'Thomas Edison', 'Nhà phát minh người Mỹ!'),
('gen_h_005', 'general_knowledge', 'hard', 'Khủng long tuyệt chủng cách đây bao nhiêu năm?', '65 triệu năm', 'Rất rất lâu rồi!'),
('gen_h_006', 'general_knowledge', 'hard', 'Núi cao nhất Việt Nam là gì?', 'Fansipan', 'Ở Lào Cai!'),
('gen_h_007', 'general_knowledge', 'hard', 'Ai đã chiến thắng trận Bạch Đằng năm 938?', 'Ngô Quyền', 'Dùng cọc nhọn!'),
('gen_h_008', 'general_knowledge', 'hard', 'Hệ mặt trời có mấy hành tinh?', '8', 'Sao Diêm Vương không tính!'),
('gen_h_009', 'general_knowledge', 'hard', 'Vịnh Hạ Long ở tỉnh nào?', 'Quảng Ninh', 'Miền Bắc Việt Nam!'),
('gen_h_010', 'general_knowledge', 'hard', 'Con vật nào có thể tái sinh đuôi khi bị đứt?', 'thằn lằn', 'Bò trên tường!'),
('gen_h_011', 'general_knowledge', 'hard', 'Ngày Quốc khánh Việt Nam là ngày nào?', '2 tháng 9', 'Tháng 9!'),
('gen_h_012', 'general_knowledge', 'hard', 'Cây tre là biểu tượng gì của Việt Nam?', 'sự kiên cường, bền bỉ', 'Uốn cong nhưng không gãy!')
ON CONFLICT (code) DO NOTHING;
