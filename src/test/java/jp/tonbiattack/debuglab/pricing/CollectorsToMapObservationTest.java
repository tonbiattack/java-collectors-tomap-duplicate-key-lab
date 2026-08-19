package jp.tonbiattack.debuglab.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class CollectorsToMapObservationTest {

    @Test
    void noMergeFunctionRejectsDuplicateKeyButLaterValueMergeCollectsTheSnapshot() {
        List<PriceUpdate> updates = List.of(
                new PriceUpdate("SKU-01", 100),
                new PriceUpdate("SKU-02", 200),
                new PriceUpdate("SKU-01", 120)
        );

        IllegalStateException duplicateKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> updates.stream().collect(Collectors.toMap(
                        PriceUpdate::sku,
                        PriceUpdate::priceYen))
        );

        Map<String, Integer> laterValueWins = updates.stream().collect(Collectors.toMap(
                PriceUpdate::sku,
                PriceUpdate::priceYen,
                (earlier, later) -> later
        ));

        assertAll(
                () -> assertTrue(duplicateKeyFailure.getMessage().contains("Duplicate key SKU-01"),
                        "例外メッセージに重複したSKUが含まれる"),
                () -> assertEquals(Map.of("SKU-01", 120, "SKU-02", 200), laterValueWins)
        );
    }
}
