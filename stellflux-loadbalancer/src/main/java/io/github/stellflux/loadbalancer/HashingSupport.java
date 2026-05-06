package io.github.stellflux.loadbalancer;

/** 哈希辅助工具。 */
final class HashingSupport {

    private static final long FNV_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    private HashingSupport() {}

    /**
     * 计算 64 位 FNV-1a 哈希值。
     *
     * @param value 输入字符串
     * @return 哈希值
     */
    static long hash64(String value) {
        long hash = FNV_64_OFFSET_BASIS;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= FNV_64_PRIME;
        }
        return mix64(hash);
    }

    /**
     * 计算正数哈希值。
     *
     * @param value 输入字符串
     * @return 正数哈希值
     */
    static long positiveHash64(String value) {
        return hash64(value) & Long.MAX_VALUE;
    }

    /**
     * 将哈希值映射到 (0, 1] 区间。
     *
     * @param hash 哈希值
     * @return 区间值
     */
    static double toUnitInterval(long hash) {
        double normalized = ((hash >>> 11) + 1D) / ((1L << 53) + 1D);
        return Math.min(1D, Math.max(Double.MIN_VALUE, normalized));
    }

    private static long mix64(long value) {
        long hash = value;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return hash;
    }
}
