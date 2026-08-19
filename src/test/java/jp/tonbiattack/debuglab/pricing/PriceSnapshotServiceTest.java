package jp.tonbiattack.debuglab.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PriceSnapshotServiceTest {

    @Test
    void duplicateSku_updatesUseTheLaterValueAndPublishTheNewSnapshot() {
        PriceSnapshotService service = new PriceSnapshotService();
        service.refresh(List.of(new PriceUpdate("SKU-01", 80)));

        RefreshOutcome outcome = service.refresh(List.of(
                new PriceUpdate("SKU-01", 100),
                new PriceUpdate("SKU-02", 200),
                new PriceUpdate("SKU-01", 120)
        ));

        assertAll(
                () -> assertEquals(RefreshOutcome.PUBLISHED, outcome,
                        "同じSKUの後着更新があっても新しいスナップショットを公開する"),
                () -> assertEquals(
                        Map.of("SKU-01", 120, "SKU-02", 200),
                        service.currentPrices(),
                        "最終スナップショットには後着のSKU-01価格と新SKUを保存する"),
                () -> assertEquals(2, service.publishedVersion(),
                        "初期公開と重複SKUを含む更新の二回を公開済みとして数える")
        );
    }

    @Test
    void uniqueSku_updatesRemainPublished() {
        PriceSnapshotService service = new PriceSnapshotService();

        RefreshOutcome outcome = service.refresh(List.of(
                new PriceUpdate("SKU-01", 100),
                new PriceUpdate("SKU-02", 200)
        ));

        assertAll(
                () -> assertEquals(RefreshOutcome.PUBLISHED, outcome),
                () -> assertEquals(Map.of("SKU-01", 100, "SKU-02", 200), service.currentPrices()),
                () -> assertEquals(1, service.publishedVersion())
        );
    }
}
