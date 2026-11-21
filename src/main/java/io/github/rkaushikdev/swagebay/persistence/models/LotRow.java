package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One batch of film on the shelf, which expires after it arrived and never holds fewer than no
 * sheets.
 */
@Entity
@Table(
        name = "lot",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_lot_lot_number",
                    columnNames = {"lot_number"})
        },
        indexes = {@Index(name = "ix_lot_film_size_expiry", columnList = "film_key, size, expires_on")})
@Check(name = "ck_lot_sheets_not_negative", constraints = "sheets >= 0")
@Check(name = "ck_lot_expires_after_arrival", constraints = "expires_on > received_on")
public class LotRow extends Keyed {

    @Column(name = "lot_number", length = 40, nullable = false)
    private String lotNumber;

    @Column(name = "film_key", length = 20, nullable = false)
    private String filmKey;

    @Column(name = "size", length = 20, nullable = false)
    private String size;

    @Column(name = "sheets", nullable = false)
    private Integer sheets;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getFilmKey() {
        return filmKey;
    }

    public void setFilmKey(String filmKey) {
        this.filmKey = filmKey;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public int getSheets() {
        return sheets;
    }

    public void setSheets(int sheets) {
        this.sheets = sheets;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public void setReceivedOn(LocalDate receivedOn) {
        this.receivedOn = receivedOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }
}
