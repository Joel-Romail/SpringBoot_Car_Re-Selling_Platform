package com.unicorn2.perekupi.services;

import com.unicorn2.perekupi.models.Car;
import com.unicorn2.perekupi.models.CarRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class SsScraperService {

    private static final String BASE = "http://proxy.alpha-host.xyz";
    private static final String TARGET = "http://proxy.alpha-host.xyz/lv/transport/cars/bmw/";
    private static final String PLACEHOLDER_IMG = "http://proxy2.alpha-host.xyz/img/buy/auto.lv.gif";

    private final CarRepository carRepository;

    @Value("${perekupi.scrape.timeout-ms:25000}")
    private int timeoutMs;

    @Value("${perekupi.scrape.retries:2}") // number of retries after the first attempt (so total attempts = retries + 1)
    private int maxRetries;

    public SsScraperService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    /** Scrape first page, insert only new cars, STOP at the first existing match. */
    @Transactional
    public SyncResult syncBmw() throws IOException {
        System.out.println("STARTED scraping");
        Document doc = fetch(TARGET);

        Elements tables = doc.select("table[alignment=center], table[align=center], table[alignment=centre]");
        int inserted = 0;
        int parsed = 0;
        boolean stoppedOnExisting = false;

        outer:
        for (Element t : tables) {
            for (Element tr : t.select("tr")) {
                Element descA = tr.selectFirst("td.msg2 a");
                if (descA == null) continue;

                parsed++;

                String carId = bestCarId(tr, descA);
                if (carId == null || carId.isBlank()) continue;

                // Early stop: first known listing implies older ones below are already processed
                List<Car> existing = carRepository.findByCarId(carId);
                if (existing != null && !existing.isEmpty()) {
                    stoppedOnExisting = true;
                    break outer;
                }

                // Build and insert new record
                Element img = tr.selectFirst("td.msga2 img");
                String picture = img != null ? img.attr("src") : "";
                if (picture.startsWith("/")) picture = BASE + picture;
                if (picture.startsWith("//")) picture = "https:" + picture;
                if (Objects.equals(picture, PLACEHOLDER_IMG)) continue;

                Elements o = tr.select("td.msga2-o.pp6");
                Element r = tr.selectFirst("td.msga2-r.pp6");

                String model = "", year = "", volume = "", mileage = "", price = "";
                if (o.size() >= 5) {
                    model = text(o.get(0)); year = text(o.get(1)); volume = text(o.get(2));
                    mileage = text(o.get(3)); price = text(o.get(4));
                } else if (o.size() == 4) {
                    model = text(o.get(0)); year = text(o.get(1)); volume = text(o.get(2));
                    price = text(o.get(3)); mileage = text(r);
                } else {
                    if (o.size() > 0) model = text(o.get(0));
                    if (o.size() > 1) year = text(o.get(1));
                    if (o.size() > 2) volume = text(o.get(2));
                    if (o.size() > 3) mileage = text(o.get(3));
                    price = o.size() > 4 ? text(o.get(4)) : "";
                }

                Car car = new Car();
                car.setCarId(carId);
                car.setCompany("BMW");
                car.setNew(true); // mark freshly-scraped inserts as NEW
                car.setPictureUrl(picture);
                car.setDescriptionDisplayName(clean(descA.text()));
                car.setDescriptionUrl(absUrl(descA.attr("href")));
                car.setModel(clean(model));
                car.setYear(clean(year));              // String
                car.setVolume(clean(volume));
                car.setMileage(parseMileage(mileage)); // int
                car.setPrice(parsePrice(price));       // robust thousands/decimal handling
                car.setMark(evaluateCarMark(car));
                carRepository.save(car);
                inserted++;
            }
        }
        System.out.println("FINISHED scraping");

        return new SyncResult(inserted, parsed, stoppedOnExisting);
    }

    // -------------------- Car evaluation logic --------------------
    private double evaluateCarMark(Car candidate) {
        List<Car> comps = carRepository.findByCompanyAndModelAndYear(
                candidate.getCompany(), candidate.getModel(), candidate.getYear());

        // Use comps with valid positive numbers
        List<Car> valid = comps.stream()
                .filter(c -> c.getPrice() > 0)
                .filter(c -> c.getMileage() > 0)
                .toList();

        if (valid.isEmpty()) return 2.5; // no comps → neutral

        double[] prices   = valid.stream().mapToDouble(c -> c.getPrice()).toArray();
        double[] mileages = valid.stream().mapToDouble(c -> c.getMileage()).toArray();

        double priceScore   = avgLogisticScoreLowerIsBetter(candidate.getPrice(), prices);
        double mileageScore = avgLogisticScoreLowerIsBetter(
        (double) candidate.getMileage(), mileages);

        double combined = (0.60 * priceScore) + (0.40 * mileageScore);
        return roundTo1(clamp(combined * 5.0, 0.0, 5.0));
    }

    /** Score in [0..1] vs average: lower-than-average → closer to 1; above-average → closer to 0. */
    private double avgLogisticScoreLowerIsBetter(Double candidateValue, double[] values) {
        if (candidateValue == null || candidateValue <= 0 || values.length == 0) return 0.5;

        double mean = mean(values);
        double std  = stddev(values, mean);

        // If no spread, be neutral
        if (std <= 1e-9) return 0.5;

        double z = (candidateValue - mean) / std; // lower is better → negative z → higher score
        double score = 1.0 / (1.0 + Math.exp(z)); // logistic
        return clamp(score, 0.0, 1.0);
    }

    private static double mean(double[] v) {
        return Arrays.stream(v).average().orElse(Double.NaN);
    }

    private static double stddev(double[] v, double mean) {
        if (v.length == 0) return 0.0;
        double var = Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / v.length; // population std
        return Math.sqrt(var);
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static double roundTo1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }


    // -------------------- Networking (timeout + retry) --------------------

    private Document fetch(String url) throws IOException {
        IOException last = null;
        int attempts = maxRetries + 1; // first attempt + retries
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return Jsoup
                        .connect(url)
                        .timeout(timeoutMs)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                 + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                 + "Chrome/120.0 Safari/537.36 PerekupiBot/1.0")
                        .referrer("https://www.google.com/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "lv-LV,lv;q=0.9,en;q=0.8")
                        .header("Cache-Control", "no-cache")
                        .ignoreHttpErrors(true)
                        .maxBodySize(0)
                        .get();
            } catch (SocketTimeoutException | java.net.UnknownHostException e) {
                last = e;
                // simple backoff: 0.5s, then +1s each retry
                try { Thread.sleep(500L + attempt * 1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (IOException e) {
                last = e;
                break; // other IO issues -> don't keep retrying aggressively
            }
        }
        throw (last != null) ? last : new IOException("Unknown network error");
    }
    
    // -------------------- Parsing helpers --------------------

    private String bestCarId(Element tr, Element descA) {
        String id = tr != null ? tr.id() : null;
        if (id != null && !id.isBlank()) return id;

        String href = descA != null ? descA.attr("href") : null;
        href = absUrl(href);
        if (href != null && !href.isBlank()) return href;

        String title = descA != null ? clean(descA.text()) : "";
        return title.isBlank() ? null : "title:" + title;
    }

    private String text(Element td) {
        if (td == null) return "";
        Element a = td.selectFirst("a");
        return clean(a != null ? a.text() : td.text());
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String absUrl(String href) {
        if (href == null || href.isBlank()) return null;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (!href.startsWith("/")) href = "/" + href;
        return BASE + href;
    }

    private int parseMileage(String s) {
        try {
            String d = s
                    .replaceAll(" tūkst.", "000")
                    .replaceAll("[^0-9]", "");
            if (d.isEmpty()) return 0;
            return Integer.parseInt(d);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Handles 19 800 / 19,800 / 19.800 (thousands) and optional decimal part. */
    private double parsePrice(String s) {
        try {
            if (s == null) return 0d;
            String val = s.replace('\u00A0',' ')
                          .replaceAll("[^0-9., ]", "")
                          .trim();
            if (val.isEmpty()) return 0d;

            boolean hasComma = val.indexOf(',') >= 0;
            boolean hasDot   = val.indexOf('.') >= 0;

            if (hasComma && hasDot) {
                int lastComma = val.lastIndexOf(',');
                int lastDot   = val.lastIndexOf('.');
                int decPos    = Math.max(lastComma, lastDot);
                String intPart  = val.substring(0, decPos).replaceAll("[^0-9]", "");
                String fracPart = val.substring(decPos + 1).replaceAll("[^0-9]", "");
                if (fracPart.isEmpty()) fracPart = "0";
                return Double.parseDouble(intPart + "." + fracPart);
            }

            val = val.replaceAll(" +", "");
            hasComma = val.indexOf(',') >= 0;
            hasDot   = val.indexOf('.') >= 0;

            if (hasComma ^ hasDot) {
                char sep = hasComma ? ',' : '.';
                int last = val.lastIndexOf(sep);
                boolean multiple = val.indexOf(sep) != last;
                String after = val.substring(last + 1);
                if (multiple || after.matches("\\d{3}")) {
                    String digits = val.replace(String.valueOf(sep), "");
                    return Double.parseDouble(digits);
                } else {
                    String intPart  = val.substring(0, last).replaceAll("[^0-9]", "");
                    String fracPart = after.replaceAll("[^0-9]", "");
                    if (fracPart.isEmpty()) fracPart = "0";
                    return Double.parseDouble(intPart + "." + fracPart);
                }
            }

            String digitsOnly = val.replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) return 0d;
            return Double.parseDouble(digitsOnly);

        } catch (Exception e) {
            return 0d;
        }
    }

    // -------------------- DTO --------------------
    public record SyncResult(int inserted, int parsed, boolean stoppedOnExisting) {}
}
