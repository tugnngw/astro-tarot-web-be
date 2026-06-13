# Các thư viện cho Bản đồ sao & Chiêm tinh (Star Map / Astrology Chart)

## 1. Backend - Tính toán Chiêm tinh (Java)

### swisseph-java (Swiss Ephemeris port)

**Cách hoạt động:**
- Là một port Java của Swiss Ephemeris, thư viện chuẩn công nghiệp để tính toán vị trí hành tinh, mặt trời, mặt trăng, các điểm Lagrange, tiểu hành tinh.
- Sử dụng dữ liệu ephemeris DE431 từ NASA JPL, bao phủ thời gian từ 13201 TCN đến 17191 CN.
- Thực hiện tất cả các phép biến đổi từ khung thời gian quán tính của JPL DE430/431 sang hệ quy chiếu cho tọa độ chiêm tinh (true equinox of date), bao gồm cả các hiệu chỉnh như aberration tương đối, lệch ánh sáng trong trường hấp dẫn của Mặt Trời.
- Cung cấp 3 ephemerides: JPL DE431 (gốc), Swiss Ephemeris nén (mặc định), và lý thuyết bán phân tích của Steve Moshier.
- Tính toán nhanh: trên máy Linux test, tính 10,000 bộ vị trí hành tinh (11 hành tinh) trong <3 giây.

**Cài đặt:**
```bash
git clone https://github.com/thurnerru/swisseph.git
cd swisseph
mvn clean install
```

**Maven:**
```xml
<dependency>
    <groupId>swisseph</groupId>
    <artifactId>swisseph</artifactId>
    <version>2.10.03</version>
</dependency>
```

**Sử dụng cơ bản:**
```java
Swisseph swiss = new Swisseph();
double[] positions = swiss.calcPlanets(year, month, day, hour, minute, second, timezone);
// Trả về mảng vị trí kinh độ, vĩ độ, khoảng cách của các hành tinh
```

### AstroWebService

**Cách hoạt động:**
- Web service đơn giản cho phép người dùng lấy vị trí hành tinh và cung (cusps).
- Sử dụng Swiss Ephemeris để tính toán.
- Cung cấp API REST để tính toán bản đồ sao.

**Endpoints:**
- `GET /api/radix?date=YYYY-MM-DD&time=HH:MM:SS&timezone=timezone&lat=latitude&lon=longitude`
  - Trả về vị trí hành tinh và cung cho bản đồ sinh (natal chart)
- `GET /api/transit?date=YYYY-MM-DD&time=HH:MM:SS`
  - Trả về vị trí hành tinh hiện tại

**Cài đặt:**
1. Tải astrologyAPI.jar từ https://github.com/Kibo/AstroAPI
2. Deploy vào local Maven repo:
```bash
mvn deploy:deploy-file -DgroupId=cz.kibo.api -DartifactId=astrologyAPI -Dversion=1.0.0 -Durl=file:./localMavenRepo/ -DrepositoryId=localMavenRepo -DupdateReleaseInfo=true -Dfile=astroAPI-1.0.0.jar
```
3. Build và chạy:
```bash
mvn clean package -Pjar
java -jar target/webservice.jar
```

## 2. Frontend - Hiển thị Bản đồ sao (JavaScript/TypeScript)

### AstroChart / AstroDraw/AstroChart

**Cách hoạt động:**
- Thư viện TypeScript thuần để tạo biểu đồ SVG hiển thị hành tinh trong chiêm tinh.
- **KHÔNG** tính toán vị trí hành tinh (chỉ vẽ).
- Sử dụng SVG để render, không phụ thuộc vào thư viện nào khác.
- Hỗ trợ nhiều loại biểu đồ: radix (natal), transit, progression, solar return.
- Có thể tùy chỉnh giao diện qua settings.js.

**Cấu trúc dữ liệu đầu vào:**
```json
{
  "planets": {"Moon":[0], "Sun":[30], "Mercury":[60], ... },
  "cusps":[300, 340, 30, 60, 75, 90, 116, 172, 210, 236, 250, 274]	
}
```
- `planets`: Đối tượng với tên hành tinh và kinh độ (độ).
- `cusps`: Mảng 12 cung (cung đầu tiên bắt đầu từ 0 độ Bạch Dương).

**Sử dụng cơ bản:**
```html
<div id="paper" style="width:800px; height:800px;"></div>
<script src="astrochart.min.js"></script>
<script>
  var chart = new astrology.Chart('paper', 800, 800);
  chart.radix(data); // Vẽ biểu đồ radix
  chart.transit(data); // Vẽ biểu đồ transit
</script>
```

**Tính năng:**
- Hiển thị 13 điểm: Sun, Moon, Mercury, Venus, Mars, Jupiter, Saturn, Uranus, Neptune, Pluto, Chiron, Lilith, NNode.
- Hỗ trợ zoom, pan, animation.
- Có thể vẽ nhiều biểu đồ trên cùng trang.

### circular-natal-horoscope-js

**Cách hoạt động:**
- Render biểu đồ chiêm tinh dạng tròn với các đường aspect.
- Nhẹ, dễ tích hợp, hỗ trợ nhiều hệ thống nhà (Placidus, Koch, Equal, Whole Sign).
- Sử dụng Canvas hoặc SVG để vẽ.

**Sử dụng cơ bản:**
```javascript
const chart = new NatalChart({
  parent: document.getElementById('chart'),
  planets: { /* dữ liệu vị trí hành tinh */ },
  houses: [ /* cung nhà */ ],
  aspects: true,
  aspectColors: { /* màu sắc cho các aspect */ }
});
chart.draw();
```

### d3-celestial

**Cách hoạt động:**
- Vẽ bản đồ bầu trời (sky map) với các chòm sao, tên sao, sử dụng D3.js.
- Dùng GeoJSON để mô tả dữ liệu thiên văn.
- Hỗ trợ nhiều hệ quy chiếu: equatorial, ecliptic, galactic, supergalactic.
- Có thể hiển thị: sao, DSOs (deep space objects), chòm sao, Milky Way, hành tinh, đường lưới (graticule).

**Cấu trúc cấu hình:**
```javascript
var config = {
  width: 0, // 0 = full parent width
  projection: "aitoff", // map projection
  transform: "equatorial", // coordinate system
  center: null, // [lon, lat, orientation]
  geopos: null, // [lat, lon] - vị trí địa lý
  zoomlevel: null,
  stars: {
    show: true,
    limit: 6, // hiện sao sáng hơn magnitude 6
    colors: true, // hiện màu sắc quang phổ
    size: 7, // kích thước tối đa
    data: 'stars.6.json' // nguồn dữ liệu
  },
  dsos: {
    show: true,
    limit: 6,
    symbols: { // hình dạng cho DSOs
      gg: {shape: "circle", fill: "#ff0000"}, // galaxy cluster
      g:  {shape: "ellipse", fill: "#ff0000"}, // galaxy
      oc: {shape: "circle", fill: "#ffcc00"}, // open cluster
      gc: {shape: "circle", fill: "#ff9900"}, // globular cluster
      en: {shape: "square", fill: "#ff00cc"}, // emission nebula
      pn: {shape: "diamond", fill: "#00cccc"}, // planetary nebula
      snr:{shape: "diamond", fill: "#ff00cc"} // supernova remnant
    }
  },
  constellations: {
    names: true,
    lines: true,
    bounds: false
  },
  mw: {
    show: true, // Milky Way
    style: { fill: "#ffffff", opacity: 0.15 }
  },
  lines: {
    graticule: { show: true, stroke: "#cccccc", width: 0.6 },
    equatorial: { show: true, stroke: "#aaaaaa", width: 1.3 },
    ecliptic: { show: true, stroke: "#66cc66", width: 1.3 }
  }
};
Celestial.display(config);
```

**Thêm dữ liệu tùy chỉnh:**
- Dùng `Celestial.add()` để thêm GeoJSON data.
- Có thể thêm điểm, đường, đa giác.
- Hỗ trợ chuyển đổi tọa độ giữa các hệ quy chiếu.

**Ví dụ thêm đường:**
```javascript
var jsonLine = {
  "type":"FeatureCollection",
  "features":[{
    "type":"Feature",
    "id":"SummerTriangle",
    "properties":{"n":"Summer Triangle", "loc":[-67.5, 52]},
    "geometry":{
      "type":"MultiLineString",
      "coordinates":[[[-80.7653, 38.7837],[-62.3042, 8.8683],[-49.642, 45.2803],[-80.7653, 38.7837]]]
    }
  }]
};

Celestial.add({
  type: 'json',
  callback: function(error, json) {
    if (error) return console.warn(error);
    var asterism = Celestial.getData(jsonLine, config.transform);
    Celestial.container.selectAll(".asterisms")
      .data(asterism.features)
      .enter().append("path")
      .attr("class", "ast");
    Celestial.redraw();
  },
  redraw: function() {
    Celestial.container.selectAll(".ast").each(function(d) {
      Celestial.setStyle(lineStyle);
      Celestial.map(d);
      Celestial.context.fill();
      Celestial.context.stroke();
      // Vẽ tên
      if (Celestial.clip(d.properties.loc)) {
        var pt = Celestial.mapProjection(d.properties.loc);
        Celestial.setTextStyle(textStyle);
        Celestial.context.fillText(d.properties.n, pt[0], pt[1]);
      }
    });
  }
});
```

## 3. Dữ liệu & Tài nguyên

### Swiss Ephemeris Data Files
- Cần thiết cho swisseph-java: file dữ liệu vị trí hành tinh từ năm 1800-2399 (hoặc mở rộng).
- Tải từ: ftp://ssd.jpl.nasa.gov/pub/eph/planets/bsp/ hoặc từ swisseph repo.

### Constellation Data
- IAU: Dữ liệu ranh giới chòm sao chuẩn quốc tế.
- Hipparcos Catalog: Catalog sao chính xác.

## 4. Luồng xử lý chi tiết từ Profile User → Bản đồ sao

### 4.1. Dữ liệu đầu vào từ User Profile

User cung cấp thông tin:
- **Họ tên**: Hiển thị trên bản đồ (không dùng cho tính toán)
- **Ngày sinh**: DD/MM/YYYY (ví dụ: 15/06/1990)
- **Giờ sinh**: HH:MM (24h format, ví dụ: 14:30)
- **Nơi sinh**: Thành phố/Địa điểm → cần convert sang **Vĩ độ (Latitude)** và **Kinh độ (Longitude)**
  - Ví dụ: Hà Nội → lat: 21.0285°N, lon: 105.8542°E
- **Múi giờ**: UTC offset hoặc timezone (ví dụ: Asia/Ho_Chi_Minh = UTC+7)

### 4.2. Backend Processing với swisseph-java

**Bước 1: Convert thời gian**
```java
// Input từ user
int year = 1990;
int month = 6;
int day = 15;
int hour = 14; // Giờ local
int minute = 30;
String timezone = "Asia/Ho_Chi_Minh"; // UTC+7

// Convert sang Julian Day Number (JDN) - thời gian thiên văn học chuẩn
double julianDay = SweDate.getJulDay(year, month, day, hour + minute/60.0, SweDate.SE_GREGORIAN);

// Điều chỉnh theo timezone (UTC+7 → trừ 7 giờ)
double julianDayUTC = julianDay - (7.0 / 24.0);
```

**Bước 2: Tính vị trí hành tinh (Planetary Positions)**
```java
// Khởi tạo Swiss Ephemeris
SwissEph swissEph = new SwissEph();

// Các hành tinh cần tính
int[] planets = {
    SweConst.SE_SUN,      // Mặt Trời
    SweConst.SE_MOON,     // Mặt Trăng
    SweConst.SE_MERCURY,  // Sao Thủy
    SweConst.SE_VENUS,    // Sao Kim
    SweConst.SE_MARS,     // Sao Hỏa
    SweConst.SE_JUPITER,  // Sao Mộc
    SweConst.SE_SATURN,   // Sao Thổ
    SweConst.SE_URANUS,   // Sao Thiên Vương
    SweConst.SE_NEPTUNE,  // Sao Hải Vương
    SweConst.SE_PLUTO     // Sao Diêm Vương
};

// Tính từng hành tinh
for (int planet : planets) {
    double[] result = new double[6]; // [longitude, latitude, distance, speedLong, speedLat, speedDist]
    StringBuilder error = new StringBuilder();
    
    // Tính vị trí: flag = SEFLG_EQUATORIAL (tọa độ xích đạo) hoặc SEFLG_TOPOCTR (từ vị trí observer)
    int flag = SweConst.SEFLG_EQUATORIAL;
    int returnCode = swissEph.calc_ut(julianDayUTC, planet, flag, result, error);
    
    // result[0] = Kinh độ (Longitude) trong độ (0-360°)
    // result[1] = Vĩ độ (Latitude) 
    // result[2] = Khoảng cách (AU)
    // result[3] = Tốc độ di chuyển theo kinh độ (độ/ngày)
}
```

**Bước 3: Tính Nhà (Houses) và Ascendant**
```java
// Tọa độ địa lý (ví dụ Hà Nội)
double latitude = 21.0285;
double longitude = 105.8542;

// Hệ thống nhà: Placidus (phổ biến nhất), Koch, Equal, Whole Sign...
int houseSystem = SweConst.SE_HSYS_PLACIDUS;

// Mảng kết quả: 13 phần tử [cusp1, cusp2, ..., cusp12, ascendant]
double[] houses = new double[13];

// Tính nhà
int returnCode = swissEph.houses_ex(julianDayUTC, SweConst.SEFLG_EQUATORIAL, latitude, longitude, houseSystem, houses);

// Kết quả:
// houses[0] = Cusp 1 (Ascendant - Điểm mọc) - quan trọng nhất
// houses[1] = Cusp 2
// ...
// houses[11] = Cusp 12
// houses[12] = MC (Midheaven - Điểm giữa trời)
```

**Bước 4: Tính các điểm quan trọng khác**
```java
// Tính Lunar Nodes (Bắc Giao điểm Mặt Trăng = Rahu/Karma)
double[] nodes = new double[6];
swissEph.calc_ut(julianDayUTC, SweConst.SE_TRUE_NODE, SweConst.SEFLG_EQUATORIAL, nodes, error);

// Tính Lilith (Black Moon / Dark Moon)
double[] lilith = new double[6];
swissEph.calc_ut(julianDayUTC, SweConst.SE_MEAN_APOG, SweConst.SEFLG_EQUATORIAL, lilith, error);

// Chiron (tiểu hành tinh)
double[] chiron = new double[6];
swissEph.calc_ut(julianDayUTC, SweConst.SE_CHIRON, SweConst.SEFLG_EQUATORIAL, chiron, error);
```

**Bước 5: Tính Aspect (Góc chiếu giữa các hành tinh)**
```java
// Ví dụ: Tính góc giữa Mặt Trời và Mặt Trăng
double sunLongitude = sunResult[0];
double moonLongitude = moonResult[0];
double aspectAngle = Math.abs(sunLongitude - moonLongitude);
if (aspectAngle > 180) aspectAngle = 360 - aspectAngle;

// Các aspect chính:
// 0° = Conjunction (hợp)
// 60° = Sextile (lục hợp) ± 6°
// 90° = Square (tứ phân) ± 8°
// 120° = Trine (tam hợp) ± 8°
// 180° = Opposition (đối) ± 8°

// Kiểm tra aspect
String aspectType;
if (aspectAngle >= 174 && aspectAngle <= 186) aspectType = "Opposition";
else if (aspectAngle >= 114 && aspectAngle <= 126) aspectType = "Trine";
else if (aspectAngle >= 84 && aspectAngle <= 96) aspectType = "Square";
else if (aspectAngle >= 54 && aspectAngle <= 66) aspectType = "Sextile";
else if (aspectAngle <= 6) aspectType = "Conjunction";
```

**Bước 6: Tính Zodiac Sign cho mỗi hành tinh**
```java
// Cung hoàng đạo (0-30° mỗi cung)
String[] signs = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", 
                  "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};

int signIndex = (int)(planetLongitude / 30) % 12;
String zodiacSign = signs[signIndex];
double degreeInSign = planetLongitude % 30;
```

### 4.3. Kết quả trả về từ Backend (JSON)

```json
{
  "name": "Nguyễn Văn A",
  "birthDate": "1990-06-15",
  "birthTime": "14:30",
  "birthPlace": "Hà Nội, Vietnam",
  "coordinates": {
    "latitude": 21.0285,
    "longitude": 105.8542,
    "timezone": "UTC+7"
  },
  "planets": {
    "Sun": {
      "longitude": 83.45,
      "latitude": 0.0,
      "sign": "Gemini",
      "degreeInSign": 23.45,
      "house": 3,
      "speed": 0.98
    },
    "Moon": {
      "longitude": 156.78,
      "latitude": -4.2,
      "sign": "Virgo",
      "degreeInSign": 6.78,
      "house": 6,
      "speed": 13.2
    },
    "Mercury": { ... },
    "Venus": { ... },
    "Mars": { ... },
    "Jupiter": { ... },
    "Saturn": { ... },
    "Uranus": { ... },
    "Neptune": { ... },
    "Pluto": { ... },
    "NorthNode": { ... },
    "Lilith": { ... },
    "Chiron": { ... }
  },
  "houses": {
    "ascendant": 23.56,
    "mc": 90.45,
    "cusps": [23.56, 45.2, 67.8, 89.3, 112.5, 145.6, 203.56, 225.2, 247.8, 269.3, 292.5, 325.6]
  },
  "aspects": [
    {
      "planet1": "Sun",
      "planet2": "Moon",
      "angle": 73.33,
      "aspectType": "Sextile",
      "orb": 0.67,
      "nature": "harmonic"
    }
  ]
}
```

### 4.4. Frontend Rendering với AstroChart

**Bước 1: Nhận dữ liệu JSON**
```javascript
fetch('/api/natal-chart', {
  method: 'POST',
  body: JSON.stringify({
    name: "Nguyễn Văn A",
    birthDate: "1990-06-15",
    birthTime: "14:30",
    latitude: 21.0285,
    longitude: 105.8542,
    timezone: "Asia/Ho_Chi_Minh"
  })
})
.then(response => response.json())
.then(data => renderChart(data));
```

**Bước 2: Chuẩn bị dữ liệu cho AstroChart**
```javascript
function renderChart(data) {
  // Convert dữ liệu backend sang format AstroChart yêu cầu
  const chartData = {
    planets: {},
    cusps: data.houses.cusps
  };
  
  // Map tên hành tinh sang format AstroChart
  const planetMapping = {
    'Sun': 'Sun',
    'Moon': 'Moon', 
    'Mercury': 'Mercury',
    'Venus': 'Venus',
    'Mars': 'Mars',
    'Jupiter': 'Jupiter',
    'Saturn': 'Saturn',
    'Uranus': 'Uranus',
    'Neptune': 'Neptune',
    'Pluto': 'Pluto',
    'NorthNode': 'NNode',
    'Lilith': 'Lilith',
    'Chiron': 'Chiron'
  };
  
  for (const [key, planet] of Object.entries(data.planets)) {
    const chartKey = planetMapping[key];
    if (chartKey) {
      // AstroChart cần mảng [longitude, latitude (optional), speed (optional)]
      chartData.planets[chartKey] = [planet.longitude];
    }
  }
  
  return chartData;
}
```

**Bước 3: Render biểu đồ**
```javascript
// Khởi tạo chart
const chart = new astrology.Chart('chart-container', 800, 800);

// Render radix (natal chart)
chart.radix(chartData);

// Thêm tên và thông tin
chart.addText(400, 50, data.name, { font: '16px Arial', fill: '#000' });
chart.addText(400, 70, `${data.birthDate} ${data.birthTime}`, { font: '12px Arial', fill: '#666' });
```

### 4.5. Luồng hoàn chỉnh (Sequence Diagram)

```
User (Browser)                                    Spring Boot Backend                                    SwissEph Library
    |                                                      |                                                    |
    |──[1] POST /api/natal-chart─────────────────────────>|                                                    |
    |   {name, birthDate, birthTime, lat, lon, timezone}   |                                                    |
    |                                                      |──[2] Convert to Julian Day──────────────────────>|
    |                                                      |   (SweDate.getJulDay)                              |
    |                                                      |<─────────────────────────────────────────────────|
    |                                                      |                                                    |
    |                                                      |──[3] calc_ut() for each planet──────────────────>|
    |                                                      |   (Sun, Moon, Mercury...Pluto)                     |
    |                                                      |<─────────────────────────────────────────────────|
    |                                                      |   Returns: [longitude, latitude, distance]         |
    |                                                      |                                                    |
    |                                                      |──[4] houses_ex()────────────────────────────────>|
    |                                                      |   (Calculate 12 houses + Ascendant)                  |
    |                                                      |<─────────────────────────────────────────────────|
    |                                                      |   Returns: [cusp1...cusp12, MC]                     |
    |                                                      |                                                    |
    |                                                      |──[5] Calculate Aspects────────────────────────────>|
    |                                                      |   (Compare angles between all planet pairs)        |
    |                                                      |<─────────────────────────────────────────────────|
    |                                                      |                                                    |
    |<─[6] JSON Response────────────────────────────────────|                                                    |
    |   {planets, houses, aspects, metadata}               |                                                    |
    |                                                      |                                                    |
    |──[7] Render with AstroChart─────────────────────────>|                                                    |
    |   (new astrology.Chart().radix(data))                |                                                    |
    |                                                      |                                                    |
    |<─[8] SVG Chart displayed to user─────────────────────|                                                    |
    |   (Circular chart with zodiac signs, planets,        |                                                    |
    |    houses, and aspect lines)                           |                                                    |
```

### 4.6. Các tính năng nâng cao

**Transit Chart (Biểu đồ vận hành)**
```java
// Tính vị trí hành tinh hiện tại
Date now = new Date();
// ... tính toán tương tự với thời điểm hiện tại

// So sánh với natal chart để tìm transit
// Ví dụ: Saturn transit vào house 7 của natal chart
```

**Progressed Chart (Biểu đồ tiến triển)**
```java
// Mỗi năm sau sinh = 1 ngày trong ephemeris
// Ví dụ: 30 tuổi = 30 ngày sau ngày sinh
int progressedDays = currentAge;
```

**Synastry (So sánh 2 chart)**
```java
// Tính aspect giữa hành tinh của người A và người B
// Ví dụ: Venus của A aspect với Mars của B
```

### 4.7. Ví dụ hoàn chỉnh: Java Service Class

```java
@Service
public class AstrologyService {
    
    private SwissEph swissEph;
    
    @PostConstruct
    public void init() {
        // Khởi tạo Swiss Ephemeris với đường dẫn đến data files
        String ephemerisPath = "/path/to/swisseph/data";
        swissEph = new SwissEph(ephemerisPath);
    }
    
    public NatalChart calculateNatalChart(String name, LocalDate birthDate, 
                                          LocalTime birthTime, double latitude, 
                                          double longitude, String timezone) {
        // Bước 1: Convert sang Julian Day
        ZonedDateTime birthDateTime = ZonedDateTime.of(birthDate, birthTime, 
                                                       ZoneId.of(timezone));
        double julianDay = SweDate.getJulDay(birthDateTime.getYear(),
                                            birthDateTime.getMonthValue(),
                                            birthDateTime.getDayOfMonth(),
                                            birthDateTime.getHour() + 
                                            birthDateTime.getMinute() / 60.0,
                                            SweDate.SE_GREGORIAN);
        
        // Điều chỉnh UTC
        double utcOffset = birthDateTime.getOffset().getTotalSeconds() / 3600.0;
        double julianDayUTC = julianDay - (utcOffset / 24.0);
        
        // Bước 2: Tính vị trí hành tinh
        Map<String, PlanetPosition> planets = calculatePlanets(julianDayUTC);
        
        // Bước 3: Tính nhà
        double[] houses = calculateHouses(julianDayUTC, latitude, longitude);
        
        // Bước 4: Tính aspect
        List<Aspect> aspects = calculateAspects(planets);
        
        // Bước 5: Build response
        return NatalChart.builder()
            .name(name)
            .birthDate(birthDate)
            .birthTime(birthTime)
            .latitude(latitude)
            .longitude(longitude)
            .planets(planets)
            .houses(houses)
            .aspects(aspects)
            .build();
    }
    
    private Map<String, PlanetPosition> calculatePlanets(double julianDay) {
        Map<String, PlanetPosition> planets = new HashMap<>();
        int[] planetIds = {SweConst.SE_SUN, SweConst.SE_MOON, ...};
        String[] planetNames = {"Sun", "Moon", "Mercury", ...};
        
        for (int i = 0; i < planetIds.length; i++) {
            double[] result = new double[6];
            StringBuilder error = new StringBuilder();
            swissEph.calc_ut(julianDay, planetIds[i], SweConst.SEFLG_EQUATORIAL, 
                           result, error);
            
            PlanetPosition position = PlanetPosition.builder()
                .longitude(result[0])
                .latitude(result[1])
                .sign(getZodiacSign(result[0]))
                .house(getHouse(result[0], houses))
                .build();
                
            planets.put(planetNames[i], position);
        }
        
        return planets;
    }
}
```

## 5. Cài đặt nhanh

### Backend với swisseph-java
```bash
# Clone và build
git clone https://github.com/thurnerru/swisseph.git
cd swisseph
mvn clean install

# Thêm vào pom.xml
<dependency>
    <groupId>swisseph</groupId>
    <artifactId>swisseph</artifactId>
    <version>2.10.03</version>
</dependency>
```

### Frontend với AstroChart
```html
<div id="paper" style="width:800px; height:800px;"></div>
<script src="https://cdn.jsdelivr.net/npm/astrochart@3.0.0/dist/astrochart.min.js"></script>
<script>
  var chart = new astrology.Chart('paper', 800, 800);
  chart.radix({
    planets: { /* dữ liệu từ backend */ },
    cusps: [ /* cung nhà */ ]
  });
</script>
```

## 6. Thư viện bổ sung cho Tarot

### Tarot Card Images
- Rider-Waite-Smith deck (public domain) hoặc các bộ bài mã nguồn mở.
- SVG Cards: tìm kiếm "svg tarot cards" trên GitHub.

### Randomization
- Java SecureRandom: dùng cho việc xào bài chính xác (cryptographically secure).
- Frontend Math.random: không nên dùng cho việc xào bài quan trọng.

## 7. Lựa chọn thay thế

### Prokerala API
- API bên ngoài: https://api.prokerala.com/
- Cung cấp API tính bản đồ sao, transit, kundli.
- Phù hợp nếu không muốn tự host tính toán.

### SwissEph4J
- Port khác của Swiss Ephemeris cho Java: https://github.com/firenzedigest/SwissEph4J

## 8. Tài liệu tham khảo

- Swiss Ephemeris Manual: https://www.astro.com/swisseph/swephinfo_e.htm
- AstroChart Documentation: https://astrodraw.github.io/
- d3-celestial Documentation: https://github.com/ofrohn/d3-celestial
- AstroWebService API: http://docs.astrologyapi.apiary.io
