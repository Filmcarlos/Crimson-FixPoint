🎯 Crimson FixPoint
ตรึงพิกัด GPS + คัดงาน "รับใกล้หมุด – ส่งวนรอบหมุด" สำหรับไรเดอร์
ใช้คู่กับแอปวิ่งงานหลัก (LINE MAN, Grab, ShopeeFood, Lalamove) เพื่อ ลดพิกัดกระโดด และ ลดระยะตีรถเปล่า
✨ 4 แท็บหลัก
1. FIXPOINT CORE: เรดาร์ล็อคหมุด
แผนที่ Leaflet + CartoDB Dark Matter แตะปักหมุดได้ทันที
วงแหวน 2 ชั้น: 🔴 รับงาน (0.1–3 กม.) และ 🚨 ส่งงาน (0.5–8 กม.) ปรับด้วยสไลเดอร์
3 โหมดล็อค: Pin-Center Micro Lock (±1.8 ม.), Merchant Centroid Stabilizer, Auto Loop-Back Compass
2. SMART ORDER FILTER: คัดงานด้วยสูตร Haversine
วิเคราะห์ 4 ค่า: ร้าน→หมุด, จุดส่ง→หมุด, ระยะรวมกลับหมุด, บาท/กม.
ตราเกรดทันที: S-RANK รับเลย · A-RANK ในโซน · WARNING ส่งออกนอกหมุด
กฎอัตโนมัติ: แจ้งเฉพาะจุดส่งในรัศมี, บล็อกงาพาน
นข้ามสะพาน/เขต, Short-Loop Priority
4. HOTZONE PINS: คลังหมุด + จุดกึ่งกลางดงร้าน
หมุดยอดฮิต: บางใหญ่, บางกรวย, ตลิ่งชัน, หลักสี่, สยาม
เพิ่มหมุดเองได้ไม่จำกัด หรือดึงพิกัดปัจจุบัน
คำนวณ Golden Centroid จากกลุ่มร้านในละแวกเดียวกัน
5. FLOATING HUD: หน้าปัดลอย + วินิจฉัยสัญญาณ
HUD ลอยแบบกะทัดรัด และโหมดเต็มจอสำหรับที่ยึดมือถือ
กราฟสัญญาณดาวเทียม (GPS / GLONASS / Galileo / BeiDou) พร้อมประวัติการล็อค
เสียงเตือนแยกโทน (ล็อคสำเร็จ / S-Rank / นอกโซน) เปิด-ปิดได้
🛠️ Tech Stack
ส่วน
เทคโนโลยี
UI
Kotlin + Jetpack Compose (Material 3, ธีม Crimson & Obsidian)
แผนที่
Leaflet.js 1.9.4 + CartoDB dark_all (Kotlin ↔ JS bridge)
คำนวณ
Haversine, Compass Bearing, Kalman Filter, Weighted Centroid
ฮาร์ดแวร์
LocationManager / Google Play Services + AudioTrack
CI/CD
GitHub Actions (ลงนาม Debug & Release อัตโนมัติ)
