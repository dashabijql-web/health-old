package com.xzkj.health.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis does not reliably hydrate abstract Number bean properties from SQL Server.
 * Keep existing Row DTO contracts working by accepting any JDBC numeric subtype.
 */
@MappedTypes(Number.class)
public class NumberTypeHandler extends BaseTypeHandler<Number> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Number parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public Number getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toNumber(rs.getObject(columnName));
    }

    @Override
    public Number getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toNumber(rs.getObject(columnIndex));
    }

    @Override
    public Number getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toNumber(cs.getObject(columnIndex));
    }

    private Number toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return new BigDecimal(text.toString().trim());
        }
        return null;
    }
}
