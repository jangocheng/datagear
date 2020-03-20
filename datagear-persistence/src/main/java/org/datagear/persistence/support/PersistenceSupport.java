/*
 * Copyright 2018 datagear.tech. All Rights Reserved.
 */

package org.datagear.persistence.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.datagear.meta.Column;
import org.datagear.meta.Table;
import org.datagear.persistence.Dialect;
import org.datagear.persistence.PersistenceException;
import org.datagear.persistence.Row;
import org.datagear.persistence.RowMapper;
import org.datagear.persistence.RowMapperException;
import org.datagear.util.JdbcSupport;
import org.datagear.util.QueryResultSet;
import org.datagear.util.Sql;
import org.datagear.util.SqlParamValue;

/**
 * 持久操作支持类。
 * 
 * @author datagear@163.com
 *
 */
public class PersistenceSupport extends JdbcSupport
{
	/**
	 * 转换为引号名字。
	 * 
	 * @param dialect
	 * @param name
	 * @return
	 */
	public String quote(Dialect dialect, String name)
	{
		return dialect.quote(name);
	}

	/**
	 * 执行数目查询。
	 * 
	 * @param cn
	 * @param sql
	 * @return
	 * @throws PersistenceException
	 */
	public long executeCountQueryWrap(Connection cn, Sql sql) throws PersistenceException
	{
		try
		{
			return executeCountQuery(cn, sql);
		}
		catch (SQLException e)
		{
			throw new PersistenceException(e);
		}
	}

	/**
	 * 执行更新。
	 * 
	 * @param cn
	 * @param sql
	 * @return
	 * @throws PersistenceException
	 */
	public int executeUpdateWrap(Connection cn, Sql sql) throws PersistenceException
	{
		try
		{
			return executeUpdate(cn, sql);
		}
		catch (SQLException e)
		{
			throw new PersistenceException(e);
		}
	}

	/**
	 * 执行列表结果查询。
	 * 
	 * @param cn
	 * @param table
	 * @param sql
	 * @param resultSetType
	 * @return
	 * @throws PersistenceException
	 */
	public List<Row> executeListQuery(Connection cn, Table table, Sql sql, int resultSetType)
			throws PersistenceException
	{
		return executeListQuery(cn, table, sql, resultSetType, 1, -1, null);
	}

	/**
	 * 执行列表结果查询。
	 * 
	 * @param cn
	 * @param table
	 * @param sql
	 * @param resultSetType
	 * @param mapper
	 *            允许为{@code null}
	 * @return
	 * @throws PersistenceException
	 */
	public List<Row> executeListQuery(Connection cn, Table table, Sql sql, int resultSetType, RowMapper mapper)
			throws PersistenceException
	{
		return executeListQuery(cn, table, sql, resultSetType, 1, -1, mapper);
	}

	/**
	 * 执行列表结果查询。
	 * 
	 * @param cn
	 * @param table
	 * @param sql
	 * @param resultSetType
	 * @param startRow
	 *            起始行号，以{@code 1}开头
	 * @param count
	 *            读取行数，如果{@code <0}，表示读取全部
	 * @param mapper
	 *            允许为{@code null}
	 * @return
	 */
	public List<Row> executeListQuery(Connection cn, Table table, Sql sql, int resultSetType, int startRow, int count,
			RowMapper mapper) throws PersistenceException
	{
		QueryResultSet qrs = null;

		try
		{
			qrs = executeQuery(cn, sql, resultSetType);
			ResultSet rs = qrs.getResultSet();

			return mapToRows(cn, table, rs, startRow, count, mapper);
		}
		catch (SQLException e)
		{
			throw new PersistenceException(e);
		}
		finally
		{
			QueryResultSet.close(qrs);
		}
	}

	/**
	 * 将结果集映射至{@linkplain Row}洌表。
	 * 
	 * @param cn
	 * @param table
	 * @param rs
	 * @param startRow
	 *            起始行，以{@code 1}开头
	 * @param count
	 *            映射行数，{@code -1}表示全部
	 * @param mapper
	 *            允许为{@code null}
	 * @return
	 * @throws RowMapperException
	 * @throws SQLException
	 */
	protected List<Row> mapToRows(Connection cn, Table table, ResultSet rs, int startRow, int count, RowMapper mapper)
			throws RowMapperException, SQLException
	{
		if (startRow < 1)
			startRow = 1;

		List<Row> resultList = new ArrayList<>();

		if (count >= 0 && startRow > 1)
			forwardBefore(rs, startRow);

		int endRow = (count >= 0 ? startRow + count : -1);

		int rowIndex = startRow;
		while (rs.next())
		{
			if (endRow >= 0 && rowIndex >= endRow)
				break;

			Row row = mapToRow(cn, table, rs, rowIndex, mapper);

			resultList.add(row);

			rowIndex++;
		}

		return resultList;
	}

	/**
	 * 将结果集行映射为{@linkplain Row}对象。
	 * 
	 * @param cn
	 * @param table
	 * @param rs
	 * @param rowIndex
	 *            行号，以{@code 1}开头
	 * @return
	 * @throws RowMapperException
	 */
	public Row mapToRow(Connection cn, Table table, ResultSet rs, int rowIndex) throws RowMapperException
	{
		return mapToRow(cn, table, rs, rowIndex, null);
	}

	/**
	 * 将结果集行映射为{@linkplain Row}对象。
	 * 
	 * @param cn
	 * @param table
	 * @param rs
	 * @param rowIndex
	 *            行号，以{@code 1}开头
	 * @param mapper
	 *            允许为{@code null}
	 * @return
	 * @throws RowMapperException
	 */
	public Row mapToRow(Connection cn, Table table, ResultSet rs, int rowIndex, RowMapper mapper)
			throws RowMapperException
	{
		if (mapper != null)
			return mapper.map(cn, table, rs, rowIndex);
		else
		{
			Row row = new Row();

			try
			{
				Column[] columns = table.getColumns();
				for (int i = 0; i < columns.length; i++)
				{
					Object value = getColumnValue(cn, rs, columns[i].getName(), columns[i].getType());
					row.put(columns[i].getName(), value);
				}
			}
			catch (SQLException e)
			{
				throw new RowMapperException(e);
			}

			return row;
		}
	}

	public Object getColumnValue(Connection cn, ResultSet rs, Column column) throws SQLException
	{
		return getColumnValue(cn, rs, column.getName(), column.getType());
	}

	public SqlParamValue createSqlParamValue(Column column, Object value)
	{
		return new SqlParamValue(value, column.getType());
	}
}
