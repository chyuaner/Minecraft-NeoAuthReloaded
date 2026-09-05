package tw.yuaner.neoauth.database;

/**
 * 當資料庫操作或連線失敗時拋出的例外。
 * <p>
 * 用於區分業務邏輯錯誤（如密碼錯誤）與連線錯誤。
 */
public class DatabaseConnectionException extends RuntimeException {
    public DatabaseConnectionException(String message) {
        super(message);
    }

    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
