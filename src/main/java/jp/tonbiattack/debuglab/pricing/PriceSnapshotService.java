package jp.tonbiattack.debuglab.pricing;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一回のリフレッシュで受け取った価格更新をSKUごとのスナップショットへ集約します。
 */
public class PriceSnapshotService {

    private Map<String, Integer> currentPrices = Map.of();
    private int publishedVersion;

    public RefreshOutcome refresh(List<PriceUpdate> updates) {
        try {
            Map<String, Integer> nextPrices = updates.stream()
                    .collect(Collectors.toMap(
                            PriceUpdate::sku,
                            PriceUpdate::priceYen,
                            (earlier, later) -> later));

            currentPrices = Map.copyOf(nextPrices);
            publishedVersion++;
            return RefreshOutcome.PUBLISHED;
        } catch (IllegalStateException ignored) {
            return RefreshOutcome.REJECTED_DUPLICATE_SKU;
        }
    }

    public Map<String, Integer> currentPrices() {
        return currentPrices;
    }

    public int publishedVersion() {
        return publishedVersion;
    }
}
