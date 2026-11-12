package lk.technocom.util;

public class DbException extends RuntimeException {
    public DbException(Throwable cause) {
        super(Db.friendly(cause), cause);
    }
}
