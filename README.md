# 🚀 Network Speed Tester

A simple, interactive web-based application to test your network's ping, download, and upload speeds. Built using **Java Spring Boot**, **Thymeleaf**, and **Bootstrap 5**.

---

## 📦 Features

- 🔁 Ping Test (measures latency to `8.8.8.8`)
- ⬇️ Download Speed Test (from a public 100MB file)
- ⬆️ Upload Speed Test (simulated 10MB data upload)
- 💻 Clean and responsive Bootstrap UI
- 📡 REST API + Web Interface

---

## 🛠 Technologies Used

- Java 17+
- Spring Boot 3.2
- Thymeleaf
- Bootstrap 5
- Maven

---

## 🧪 How to Run

1. **Clone the repository**

git clone https://github.com/karthikskumar94/NetworkSpeedTesterApplication.git
cd NetworkSpeedTesterApplication

2. **Build and run**

mvn clean install
mvn spring-boot:run

3. **Open in browser**

http://localhost:8080

🧰 API Endpoints
Endpoint	                          Method	           Description
/api/speedtest/ping	                 GET	          Test ping latency
/api/speedtest/download	             GET	          Test download speed
/api/speedtest/upload	               POST	          Test upload speed (sim)
