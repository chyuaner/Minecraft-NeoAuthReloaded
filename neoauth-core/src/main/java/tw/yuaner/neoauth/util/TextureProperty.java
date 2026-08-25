package tw.yuaner.neoauth.util;

/**
 * 玩家 Textures (皮膚與披風) 屬性封裝。
 *
 * @param value     Base64 編碼的皮膚與披風 JSON 數據
 * @param signature Mojang 或外置驗證站簽署的 RSA 簽名 (可為空)
 */
public record TextureProperty(String value, String signature) {
    public boolean hasSignature() {
        return signature != null && !signature.isBlank();
    }
}
