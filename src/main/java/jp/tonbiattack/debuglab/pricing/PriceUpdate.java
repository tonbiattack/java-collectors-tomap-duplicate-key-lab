package jp.tonbiattack.debuglab.pricing;

/**
 * 同じSKUは入力順で後から現れた値が新しい価格とみなされます。
 */
public record PriceUpdate(String sku, int priceYen) {
}
