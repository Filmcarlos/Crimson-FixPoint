# 🎯 Crimson FixPoint
**Tactical GPS Stabilizer & Short-Loop Order Filter for Delivery Riders**  
*(ระบบตรึงสัญญาณพิกัดยุทธวิธี และตัวคัดกรองงาน "รับใกล้หมุด – ส่งวนรอบหมุด" สำหรับไรเดอร์)*

---

## 📌 แนวคิดหลัก (Core Concept)
**Crimson FixPoint** ออกแบบมาเพื่อทำงานร่วมกับแอปวิ่งงานหลัก (**LINE MAN Rider, Grab Driver, ShopeeFood, Lalamove**) โดยมีเป้าหมายหลัก 2 ประการ:
1. **รับงานใกล้หมุดที่สุด (Closest Pickup to Pin)**: ตรึงความแม่นยำสัญญาณ GPS ด้วยโหมด `Pin-Center Micro Lock` และ `Kalman Anti-Drift` พร้อมคำนวณจุดยืนกึ่งกลางดงร้านอาหาร (**Golden Centroid Point**) เพื่อลดอาการพิกัดกระโดดเวลาอยู่ใต้อาคารหรือชายคาห้าง
2. **ส่งใกล้หมุดที่สุด / วิ่งวนรอบหมุด (Closest Drop-off to Pin & Loop-Back Guard)**: กำหนดวงแหวนรัศมี 2 ชั้นรอบหมุดหลัก (**Inner Pickup Ring** & **Outer Drop-off Ring**) บนแผนที่จริงและเรดาร์ พร้อมสแกนการ์ดงานด้วยสูตร **Haversine** เพื่อคัดเฉพาะงานที่ส่งเสร็จแล้วยังอยู่ใกล้หมุดเดิม ลดระยะตีรถเปล่า (Deadhead Distance)

---

## 🔥 ฟีเจอร์หลักทั้ง 4 แท็บ (Core Features)

### 1. `FIXPOINT CORE` — เรดาร์ล็อคหมุด & แผนที่จริง CartoDB Dark Matter
- **Interactive Leaflet.js + CartoDB Dark Matter Map**: คลิก/แตะปักหมุดบนแผนที่จริงได้ทันที พร้อมวาด:
  - 🔴 **วงกลมสีแดงทึบ (Pickup Ring)**: แสดงรัศมีรับงานใกล้หมุดหลัก (`0.1 – 3.0 กม.`, ค่าเริ่มต้น `0.8 กม.`)
  - 🚨 **วงกลมเส้นประเรืองแสง (Drop-off Ring)**: แสดงรัศมีจุดส่งไม่เกินหมุดเดิม (`0.5 – 8.0 กม.`, ค่าเริ่มต้น `2.0 กม.`) ขยายขนาดตามสไลเดอร์แบบเรียลไทม์
- **Dual-Ring Tactical Radar & Crimson Shockwave**: เรดาร์กวาด 360 องศา พร้อมคลื่นกระแทกสีแดงเมื่อกด `ENGAGE FIXPOINT LOCK`
- **Platform Mode Selector**: ปรับโปรไฟล์อัตโนมัติสำหรับ `LINE MAN Rider`, `Grab Driver`, `ShopeeFood`, `Lalamove`, และ `All Apps (15Hz)`
- **3 Tactical GPS Lock Modes**:
  1. `Pin-Center Micro Lock` (ตรึงสัญญาณความถี่สูง ±1.8m)
  2. `Merchant Centroid Stabilizer` (ล็อคจุดกึ่งกลางดงร้านด้วย Kalman Filter)
  3. `Auto Loop-Back Compass` (เข็มทิศและเวลาเดินทางนำทางตีรถกลับเข้าหมุดหลักทันทีหลังส่งงานเสร็จ)

### 2. `SMART ORDER FILTER` — ระบบสแกนและคัดกรองงาน "รับใกล้-ส่งใกล้หมุด"
- **Live Incoming Order Simulator & Custom Evaluator**: จำลองการ์ดงานเด้งพร้อมแถบสแกนเลเซอร์สีแดง (**Crimson Laser Scanline**) หรือกรอกระยะจุดรับ-จุดส่งเพื่อคำนวณจริงด้วยสูตร **Haversine**
- **วิเคราะห์ 4 ค่าสำคัญแบบเรียลไทม์**:
  1. ระยะร้านค้าห่างจากหมุด (`Pickup to Pin`)
  2. ระยะจุดส่งห่างจากหมุดเดิม (`Drop-off to Pin`)
  3. ระยะวิ่งจริงรวมตีรถกลับหมุด (`Total Loop Distance`)
  4. ความคุ้มค่ารอบวิ่ง (`บาท/กม. รวมขากลับหมุด`)
- **Instant Verdict Badge**: ประทับตราเกรดงานทันที (`[S-RANK: รับเลย! รับใกล้-ส่งวนหมุด]`, `[A-RANK: ในโซน]`, หรือ `[WARNING: จุดส่งฉีกออกนอกหมุด]`)
- **Auto-Filter Rules**: สวิตช์เปิด-ปิดการแจ้งเตือนเฉพาะจุดส่งในรัศมี, บล็อกงานข้ามสะพาน/ข้ามเขต, และโหมดเน้นงานสั้นทำรอบ (`Short-Loop Priority`)

### 3. `HOTZONE PINS & CENTROID` — คลังหมุดทองคำ & คำนวณจุดกึ่งกลางดงร้าน
- **คลังหมุดยอดฮิตนนทบุรี & กรุงเทพฯ**:
  - `โซนบางใหญ่ - เซ็นทรัลเวสต์เกต / ตลาดบางใหญ่`
  - `โซนบางกรวย - พระราม 5 / ราชพฤกษ์`
  - `โซนตลิ่งชัน - บรมราชชนนี / ฟู้ดวิลล่า`
  - `โซนหลักสี่ - แจ้งวัฒนะ / ไอทีสแควร์`
  - `โซนสยาม - บรรทัดทอง / สามย่าน`
- **แตะปักหมุดบนแผนที่ / ดึงพิกัด GPS ปัจจุบัน / เพิ่มหมุดใหม่ไม่จำกัด**
- **Merchant Cluster Centroid Calculator**: เพิ่มรายชื่อร้านอาหารในละแวกนั้นเพื่อคำนวณหา **พิกัดกึ่งกลางทองคำ (Golden Centroid Point)** ที่อยู่ใกล้ทุกร้านเฉลี่ยสั้นที่สุด

### 4. `FLOATING HUD & DIAGNOSTICS` — หน้าปัดลอยมอเตอร์ไซค์ & กราฟสัญญาณดาวเทียม
- **Compact Rider Floating HUD & Fullscreen Bike Mount HUD**: แถบสถานะลอยด้านบนและโหมดขยายเต็มหน้าจอสำหรับวางโทรศัพท์บนที่ยึดมือถือมอเตอร์ไซค์
- **GNSS SNR Matrix & Drift Log**: กราฟแท่งแสดงความแรงสัญญาณดาวเทียม (`GPS`, `GLONASS`, `Galileo`, `BeiDou`) และตารางประวัติการล็อคหมุด/คัดกรองงาน
- **Synthesized Tactical Audio Alerts**: เสียงเตือนแยกโทนชัดเจน (Tactical Lock Chime, S-Rank Double Beep, Out-of-Zone Warning Buzz) พร้อมปุ่ม Mute/Unmute

---

## 🛠️ Tech Stack (สถาปัตยกรรมและเทคโนโลยี)
- **Android Native UI**: Kotlin + Jetpack Compose (Material 3 Crimson & Obsidian Cyber-Tactical Theme)
- **Interactive Map Engine**: Leaflet.js `1.9.4` + **CartoDB Dark Matter** (`dark_all`) with real-time Kotlin ↔ JavaScript bridge (`@JavascriptInterface`)
- **Geospatial & Math Engine**: Real **Haversine** great-circle distance, compass bearing calculation, Kalman smoothing filter, and Weighted Centroid algorithm
- **Hardware Telemetry & Audio**: Android `LocationManager` / Google Play Services Location + Multi-tone PCM `AudioTrack` Synthesizer
- **CI/CD Automation**: GitHub Actions (`.github/workflows/build-apk.yml`) with automated Debug & Release keystore signing

---

## 🚀 การติดตั้งและรันโปรเจกต์ (Local Setup & Build Instructions)

### 1. การรันและสร้างไฟล์ APK บนเครื่อง (Android / Gradle)
```bash
# 1. โคลนโปรเจกต์จาก GitHub
git clone https://github.com/<your-username>/crimson-fixpoint.git
cd crimson-fixpoint

# 2. รันชุดทดสอบ Unit & Robolectric Tests
gradle :app:testDebugUnitTest

# 3. สร้างไฟล์ Debug APK พร้อมติดตั้งลงมือถือทันที
gradle :app:assembleDebug
```
ไฟล์ APK จะถูกสร้างไว้ที่:
- `app/build/outputs/apk/debug/app-debug.apk`

### 2. การใช้สคริปต์ผ่าน `npm` (จาก `package.json`)
```bash
npm run test:android      # รัน Unit Tests ทั้งหมด
npm run build:apk-debug   # สร้างไฟล์ Debug APK
npm run build:apk-release # สร้างไฟล์ Release APK
```

### 3. การสร้างไฟล์ APK อัตโนมัติบน GitHub Actions (Automated CI/CD APK Build)
โปรเจกต์นี้ติดตั้งเวิร์กโฟลว์ **`.github/workflows/build-apk.yml`** เรียบร้อยแล้ว:
1. เพียง **Push โค้ดขึ้น GitHub** ที่กิ่ง `main` หรือ `master` (หรือกดปุ่ม **Run workflow** ในแท็บ **Actions**)
2. GitHub Actions จะทำการตั้งค่า JDK 17, Android SDK, สร้าง/ตรวจสอบ Keystore สำหรับลงนาม (`debug.keystore` และ `my-upload-key.jks`), รัน Unit Tests และคอมไพล์ไฟล์ APK ทั้งแบบ Debug และ Release อัตโนมัติ
3. ดาวน์โหลดไฟล์ **`CrimsonFixPoint-Debug-APK`** และ **`CrimsonFixPoint-Release-APK`** ได้ทันทีจากส่วน **Artifacts** ในหน้าสรุปผลของ GitHub Actions (และหากสร้าง Tag เช่น `v1.0.0` ระบบจะแนบไฟล์ APK ขึ้นหน้า **GitHub Releases** ให้โดยอัตโนมัติ)
