package com.example.mapsmcp.validation;

import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.models.AreaCalculationResult;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.utils.SecretMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoValidatorAndMathTest {

    private GeoValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GeoValidator();
    }

    @Test
    void shouldValidateValidCoordinates() {
        validator.validateCoordinates(22.5726, 88.3639);
        validator.validateCoordinates(new Coordinates(-33.8688, 151.2093));
    }

    @Test
    void shouldRejectInvalidLatitudeAndLongitude() {
        assertThatThrownBy(() -> validator.validateCoordinates(95.0, 88.0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid latitude");

        assertThatThrownBy(() -> validator.validateCoordinates(22.0, -185.0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid longitude");

        assertThatThrownBy(() -> validator.validateCoordinates(null, 88.0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldValidateRadiusAndLimits() {
        assertThat(validator.validateAndNormalizeRadius(null)).isEqualTo(5000);
        assertThat(validator.validateAndNormalizeRadius(2500)).isEqualTo(2500);
        assertThatThrownBy(() -> validator.validateAndNormalizeRadius(0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validateAndNormalizeRadius(60000))
                .isInstanceOf(ValidationException.class);

        assertThat(validator.validateAndNormalizeLimit(null)).isEqualTo(10);
        assertThat(validator.validateAndNormalizeLimit(25)).isEqualTo(25);
        assertThatThrownBy(() -> validator.validateAndNormalizeLimit(100))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldEnforceSsrfProtectionOnProviderUrls() {
        validator.validateProviderUrl("https://maps.googleapis.com", false);
        validator.validateProviderUrl("http://127.0.0.1:8089", true);

        assertThatThrownBy(() -> validator.validateProviderUrl("http://127.0.0.1:8080", false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SSRF Protection");

        assertThatThrownBy(() -> validator.validateProviderUrl("ftp://maps.googleapis.com", false))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldCalculateHaversineDistanceAccurately() {
        // Kolkata Airport (22.6547, 88.4467) to Howrah Station (22.5830, 88.3426) ~ 13.34 km
        double meters = GeoMathUtils.haversineDistanceMeters(22.6547, 88.4467, 22.5830, 88.3426);
        assertThat(meters).isBetween(13000.0, 13700.0);
    }

    @Test
    void shouldCalculatePolygonAreaWithJts() {
        // ~1km x 1km square near Equator
        List<Coordinates> square = List.of(
                new Coordinates(0.0, 0.0),
                new Coordinates(0.0, 0.008983),
                new Coordinates(0.008983, 0.008983),
                new Coordinates(0.008983, 0.0)
        );
        AreaCalculationResult res = GeoMathUtils.calculatePolygonArea(square);
        assertThat(res.validGeometry()).isTrue();
        assertThat(res.areaSquareKilometers()).isBetween(0.95, 1.05);
        assertThat(res.perimeterKilometers()).isBetween(3.9, 4.1);
        assertThat(res.geoJsonPolygon()).containsEntry("type", "Polygon");
    }

    @Test
    void shouldComputeSlippyMapTilesAndDecodePolyline() {
        MapTileData tile = GeoMathUtils.computeTileData(
                22.5448, 88.3426, 15,
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                "OpenStreetMap", "© OSM"
        );
        assertThat(tile.zoomLevel()).isEqualTo(15);
        assertThat(tile.tileUrl()).startsWith("https://tile.openstreetmap.org/15/");
        assertThat(tile.tileBoundingBox().northLat()).isGreaterThan(tile.tileBoundingBox().southLat());

        List<Coordinates> decoded = GeoMathUtils.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(decoded).hasSize(3);
        assertThat(decoded.getFirst().latitude()).isEqualTo(38.5);
        assertThat(decoded.getFirst().longitude()).isEqualTo(-120.2);
    }

    @Test
    void shouldMaskSecretsInUrlsAndLogs() {
        String raw = "GET https://maps.googleapis.com/maps/api/place?key=SAMPLE_KEY_VALUE&access_token=SAMPLE_TOKEN_VALUE";
        String masked = SecretMasker.maskUrlSecrets(raw);
        assertThat(masked).doesNotContain("SAMPLE_KEY_VALUE").doesNotContain("SAMPLE_TOKEN_VALUE");
        assertThat(masked).contains("key=***REDACTED***").contains("access_token=***REDACTED***");
    }
}
