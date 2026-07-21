# Neverwin Scheduler Starter 🚀

**neverwin-scheduler-starter** adalah modul Spring Boot kustom yang menyediakan mekanisme penjadwalan (*scheduling*) terpusat, dinamis, dan aman untuk arsitektur *microservices*.

Modul ini membungkus kapabilitas Spring `@Scheduled` dan integrasi **ShedLock** secara transparan, memungkinkan seluruh konfigurasi jadwal (Cron, Fixed Rate, Fixed Delay), durasi *lock*, hingga status *on/off* diatur sepenuhnya melalui `application.yml` tanpa perlu melakukan *recompile* kode.

## ✨ Fitur Utama
* **Centralized Configuration:** Semua jadwal diatur lewat YML.
* **Microservices Ready:** Terintegrasi dengan ShedLock berbasis JDBC untuk mencegah eksekusi *task* ganda antar *instance*.
* **Dynamic Toggling:** Matikan atau hidupkan *task* spesifik hanya dengan mengubah `enabled: true/false`.
* **Multi-type Scheduling:** Mendukung `cron`, `fixed-rate`, dan `fixed-delay`.
* **Startup Delay (Jitter):** Mendukung `initial-delay` untuk mencegah lonjakan CPU saat aplikasi baru menyala.

---

## 🛠️ Instalasi & Persiapan

### 1. Buat Tabel ShedLock di Database
Modul ini membutuhkan tabel untuk melacak status *lock* antar *node*. Jalankan *script* SQL berikut.

```sql
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

### 2. Import Dependensi
Jika menggunakan database default postgresql

```pom.xml
<dependency>
  <groupId>org.neverwin</groupId>
  <artifactId>neverwin-scheduler-starter</artifactId>
  <version>0.0.1</version>
</dependency>
```

Jika ingin menggunakan database selain postgresql

```pom.xml
<dependency>
    <groupId>org.neverwin</groupId>
    <artifactId>neverwin-scheduler-starter</artifactId>
    <version>0.0.1</version>
    <exclusions>
        <exclusion>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 3. Konfigurasi Database

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/neverwin_poc?currentSchema=application
    username: postgres
    password: password
```

### 4. Configuration Scheduler

```yaml
neverwin:
  scheduler:
    schema-name: "commons"       # kosongkan jika ingin di schema yang sama dengan aplikasi
    default-lock-at-most-for: "PT10M"
    tasks:
      # Contoh 1: Menggunakan Cron Expression (Nonaktif)
      scheduled1:
        enabled: false           # true jika scheduler ingin diaktifkan
        cron: "0 */5 * * * ?"    
        lock-at-most-for: "PT4M"
        lock-at-least-for: "PT1M"
        
      # Contoh 2: Menggunakan Fixed Rate dengan Initial Delay
      taskFixedRate:
        enabled: true
        fixed-rate: 10000        # Berjalan setiap 10.000 ms (10 detik)
        initial-delay: 5000      # Jeda awal 5 detik saat server pertama kali menyala
        lock-at-most-for: "PT8S" # Harus lebih kecil dari fixed-rate
        lock-at-least-for: "PT2S"
        
      # Contoh 3: Menggunakan Fixed Delay dengan Initial Delay
      taskFixedDelay:
        enabled: true
        fixed-delay: 30000       # Jeda 30 detik setelah task sebelumnya selesai
        initial-delay: 1000      # Jeda awal 1 detik
        lock-at-most-for: "PT20S"
        lock-at-least-for: "PT5S"
```

## 🛠️ Contoh Implementasi

```Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    @NeverwinScheduler("scheduled1")
    public void generateMonthlyReport() {
        log.info("Eksekusi Cron Task: scheduled1");
        // Logika bisnis Anda...
    }

    @NeverwinScheduler("taskFixedRate")
    public void syncDataRate() {
        log.info("Eksekusi Fixed Rate Task: taskFixedRate");
        // Logika bisnis Anda...
    }

    @NeverwinScheduler("taskFixedDelay")
    public void cleanupTempFiles() {
        log.info("Eksekusi Fixed Delay Task: taskFixedDelay");
        // Logika bisnis Anda...
    }
}
```

## 🔍 Observability & Logging (Correlation ID)

Modul **Neverwin-Scheduler-Starter** secara otomatis men- *generate* **Correlation ID** dan **Task Name** ke dalam MDC (*Mapped Diagnostic Context*) SLF4J setiap kali sebuah *scheduler* dieksekusi.
Fitur ini sangat krusial untuk keperluan *tracing* log jika aplikasi Anda berjalan di lingkungan *microservices* (menggunakan Kibana, Datadog, Grafana, dll), sehingga Anda bisa memfilter log berdasarkan satu siklus eksekusi *task* saja.

Agar nilai dari MDC ini muncul di *console* aplikasi, Anda perlu menambahkan format pola (*pattern*) logging. Tambahkan konfigurasi ini di file `application.yml` pada **tingkat paling atas (root level)**, sejajar dengan blok `spring:` atau `neverwin:`.

```yaml
logging:
  pattern:
    # Memanggil nilai MDC menggunakan parameter %X{taskName} dan %X{correlationId}
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%15.15t] %-5level [%X{taskName}] [%X{correlationId}] %logger{36} - %msg%n"
```

Contoh Hasil log nya

```log
2026-07-21 21:54:50.123 [ nvw-vt-sched-1] INFO  [taskFixedRate] [8f9b2c7d4a124c6e91f0b3d8a4b5c6d7] c.n.s.ReportSchedulerService - Eksekusi task dimulai...
2026-07-21 21:54:50.550 [ nvw-vt-sched-1] INFO  [taskFixedRate] [8f9b2c7d4a124c6e91f0b3d8a4b5c6d7] c.n.s.ReportSchedulerService - Mengumpulkan data laporan bulanan...
2026-07-21 21:54:52.000 [ nvw-vt-sched-1] INFO  [taskFixedRate] [8f9b2c7d4a124c6e91f0b3d8a4b5c6d7] c.n.s.ReportSchedulerService - Task selesai dieksekusi.
```