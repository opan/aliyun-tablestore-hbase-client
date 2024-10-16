package com.alicloud.tablestore.adaptor.client.util;

import com.alicloud.openservices.tablestore.AsyncClientInterface;
import com.alicloud.openservices.tablestore.TableStoreException;
import com.alicloud.openservices.tablestore.model.*;
import com.alicloud.openservices.tablestore.model.filter.CompositeColumnValueFilter;
import com.alicloud.openservices.tablestore.model.filter.ColumnValueFilter;
import com.alicloud.openservices.tablestore.model.filter.SingleColumnValueFilter;
import com.alicloud.tablestore.adaptor.DoNotRetryIOException;
import com.alicloud.tablestore.adaptor.client.OTSErrorCode;
import com.alicloud.tablestore.adaptor.filter.*;
import com.alicloud.tablestore.hbase.ColumnMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class OTSUtil {
    private static final Log LOG = LogFactory.getLog(OTSUtil.class);

    public static PrimaryKey toPrimaryKey(byte[] rowKey, String rowKeyName) {
        PrimaryKeyColumn[] primaryKeyColumns = new PrimaryKeyColumn[1];
        primaryKeyColumns[0] = new PrimaryKeyColumn(rowKeyName, PrimaryKeyValue.fromBinary(rowKey));
        return new PrimaryKey(primaryKeyColumns);
    }

    public static TimeRange toTimeRange(com.alicloud.tablestore.adaptor.struct.OTimeRange timeRange) {
        return new TimeRange(OTSUtil.formatTimestamp(timeRange.getMin()), OTSUtil.formatTimestamp(timeRange.getMax()));
    }

    private static SingleColumnValueFilter.CompareOperator toCompareOperator(
            OSingleColumnValueFilter.OCompareOp compareOp) {
        switch (compareOp) {
            case LESS:
                return SingleColumnValueFilter.CompareOperator.LESS_THAN;
            case LESS_OR_EQUAL:
                return SingleColumnValueFilter.CompareOperator.LESS_EQUAL;
            case EQUAL:
                return SingleColumnValueFilter.CompareOperator.EQUAL;
            case GREATER_OR_EQUAL:
                return SingleColumnValueFilter.CompareOperator.GREATER_EQUAL;
            case GREATER:
                return SingleColumnValueFilter.CompareOperator.GREATER_THAN;
            case NOT_EQUAL:
                return SingleColumnValueFilter.CompareOperator.NOT_EQUAL;
            default:
                return null;
        }
    }

    public static ColumnValueFilter toColumnValueFilter(RowQueryCriteria criteria, OFilter filter) {
        Preconditions.checkNotNull(filter);
        if (filter instanceof OSingleColumnValueFilter) {
            OSingleColumnValueFilter oSingleColumnValueFilter = (OSingleColumnValueFilter) filter;
            String columnName = ColumnMapping.getTablestoreColumnName(oSingleColumnValueFilter.getQualifier());
            SingleColumnValueFilter.CompareOperator compareOperator =
                    toCompareOperator(oSingleColumnValueFilter.getOperator());
            ColumnValue columnValue =
                    ColumnValue.fromBinary(oSingleColumnValueFilter.getValue());
            SingleColumnValueFilter singleColumnValueFilter =
                    new SingleColumnValueFilter(columnName, compareOperator, columnValue);
            // passIfMissing = !filterIfMissing
            singleColumnValueFilter.setPassIfMissing(!((OSingleColumnValueFilter) filter).getFilterIfMissing());
            singleColumnValueFilter.setLatestVersionsOnly(((OSingleColumnValueFilter) filter).getLatestVersionOnly());
            return singleColumnValueFilter;
        } else if (filter instanceof OFilterList) {
            CompositeColumnValueFilter.LogicOperator logicOperator = null;
            switch (((OFilterList) filter).getOperator()) {
                case MUST_PASS_ALL:
                    logicOperator = CompositeColumnValueFilter.LogicOperator.AND;
                    break;
                case MUST_PASS_ONE:
                    logicOperator = CompositeColumnValueFilter.LogicOperator.OR;
            }
            CompositeColumnValueFilter compositeFilter = new CompositeColumnValueFilter(logicOperator);
            for (OFilter filterItem : ((OFilterList) filter).getFilters()) {
                if (mayHasValueFilter(filterItem)) {
                    ColumnValueFilter columnValueFilter = toColumnValueFilter(criteria, filterItem);
                    if (columnValueFilter != null) {
                        compositeFilter.addFilter(columnValueFilter);
                    } else {
                        continue;
                    }
                } else {
                    // if non-value filter is in the filter list, we can only handle it when MUST_PASS_ALL
                    if (logicOperator != CompositeColumnValueFilter.LogicOperator.AND) {
                        throw new UnsupportedOperationException(
                                "Unsupported filter type: " + filterItem.getClass().getName() +
                                        " in filter list without MUST_PASS_ALL");
                    }
                    handleNonValueFilterForRowQueryCriteria(criteria, filterItem);
                }
            }
            if (compositeFilter.getSubFilters().size() < 1) {
                return null;
            } else if (compositeFilter.getSubFilters().size() == 1) {
                // if only one filter, return it
                return compositeFilter.getSubFilters().get(0);
            } else {
                return compositeFilter;
            }
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    public static void handleValueFilterForRowQueryCriteria(RowQueryCriteria criteria, OFilter filter) {
        ColumnValueFilter columnValueFilter = toColumnValueFilter(criteria, filter);
        if (columnValueFilter != null) {
            criteria.setFilter(columnValueFilter);
        }
    }

    public static void handleNonValueFilterForRowQueryCriteria(RowQueryCriteria criteria, OFilter filter) {
        Preconditions.checkNotNull(filter);
        if (filter instanceof OColumnPaginationFilter) {
            OColumnPaginationFilter oFilter = (OColumnPaginationFilter)filter;
            com.alicloud.openservices.tablestore.model.filter.ColumnPaginationFilter columnPaginationFilter =
                    new com.alicloud.openservices.tablestore.model.filter.ColumnPaginationFilter(oFilter.getLimit());
            if (oFilter.getColumnOffset() == null) {
                columnPaginationFilter.setOffset(oFilter.getOffset());
            } else {
                criteria.setStartColumn(ColumnMapping.getTablestoreColumnName(oFilter.getColumnOffset()));
            }
            criteria.setFilter(columnPaginationFilter);
        } else if (filter instanceof OColumnRangeFilter) {
            OColumnRangeFilter oFilter = (OColumnRangeFilter)filter;
            if (oFilter.getMinColumn() != null) {
                String colName = ColumnMapping.getTablestoreColumnName(oFilter.getMinColumn());
                if (oFilter.isMinColumnInclusive()) {
                    criteria.setStartColumn(colName);
                } else {
                    criteria.setStartColumn(colName + "\0"); // <= colName is same as < colName+1
                }
            }
            if (oFilter.getMaxColumn() != null) {
                String colName = ColumnMapping.getTablestoreColumnName(oFilter.getMaxColumn());
                if (oFilter.isMaxColumnInclusive()) {
                    criteria.setEndColumn(colName + "\0"); // <= colName is same as < colName+1
                } else {
                    criteria.setEndColumn(colName);
                }
            }
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    public static boolean mayHasValueFilter(OFilter filter) {
        if (filter == null) {
            return false;
        }
        return filter instanceof OSingleColumnValueFilter || filter instanceof OFilterList;
    }

    public static void handleFilterForRowQueryCriteria(RowQueryCriteria criteria, OFilter filter) {
        if (filter == null) {
            return;
        }
        if (mayHasValueFilter(filter)) {
            handleValueFilterForRowQueryCriteria(criteria, filter);
        } else {
            handleNonValueFilterForRowQueryCriteria(criteria, filter);
        }
    }

    public static com.alicloud.tablestore.adaptor.struct.OResult parseOTSRowToResult(Row row) {
        if (row == null) {
            return new com.alicloud.tablestore.adaptor.struct.OResult(null, new com.alicloud.tablestore.adaptor.struct.OColumnValue[0]);
        }
        byte[] rowKey = row.getPrimaryKey().getPrimaryKeyColumn(0).getValue().asBinary();
        int columnNum = row.getColumns().length;
        com.alicloud.tablestore.adaptor.struct.OColumnValue[] kvs = new com.alicloud.tablestore.adaptor.struct.OColumnValue[columnNum];

        for (int i = 0; i < columnNum; i++) {
            kvs[i] =
                    new com.alicloud.tablestore.adaptor.struct.OColumnValue(rowKey,
                            Bytes.toBytes(row.getColumns()[i].getName()), row.getColumns()[i].getTimestamp(),
                            com.alicloud.tablestore.adaptor.struct.OColumnValue.Type.PUT, row.getColumns()[i].getValue().asBinary());
        }
        return new com.alicloud.tablestore.adaptor.struct.OResult(rowKey, kvs);
    }

    public static long formatTimestamp(long timestamp) {
        if (timestamp >= 1e16 && timestamp < 1e17) { // micro seconds
            LOG.warn("use timestamp in micro seconds");
        }
        return timestamp;
    }

    public static boolean shouldRetry(Throwable ex) {
        if (ex instanceof TableStoreException) {
            String errorCode = ((TableStoreException) ex).getErrorCode();
            if (errorCode.equals(OTSErrorCode.INVALID_PARAMETER)
                    || errorCode.equals(OTSErrorCode.AUTHORIZATION_FAILURE)
                    || errorCode.equals(OTSErrorCode.INVALID_PK)
                    || errorCode.equals(OTSErrorCode.OUT_OF_COLUMN_COUNT_LIMIT)
                    || errorCode.equals(OTSErrorCode.OUT_OF_ROW_SIZE_LIMIT)
                    || errorCode.equals(OTSErrorCode.CONDITION_CHECK_FAIL)
                    || errorCode.equals(OTSErrorCode.REQUEST_TOO_LARGE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether the given keyValue represents a qualifier
     */
    public static boolean isQualifier(com.alicloud.tablestore.adaptor.struct.OColumnValue kv) {
        return kv != null && kv.getQualifier() != null;
    }

    /**
     * GetRow with timeRange [0, timestamp]
     * if kv is set, then only returns the specified column
     */
    private GetRowResponse getRowForDelete(AsyncClientInterface ots, String tableName, PrimaryKey primaryKey,
                                                 com.alicloud.tablestore.adaptor.struct.OColumnValue kv, long timestamp)
            throws IOException {
        try {
            SingleRowQueryCriteria singleRowQueryCriteria = new SingleRowQueryCriteria(tableName, primaryKey);
            // set TimeRange
            long endTimestamp = timestamp < Long.MAX_VALUE ? timestamp + 1 : Long.MAX_VALUE;
            singleRowQueryCriteria.setTimeRange(new TimeRange(0, OTSUtil.formatTimestamp(endTimestamp))); // [0, timestamp + 1)
            // set columns; if not family, only returns the specified column
            if (OTSUtil.isQualifier(kv)) {
                singleRowQueryCriteria.addColumnsToGet(ColumnMapping.getTablestoreColumnName(kv.getQualifier()));
            }
            GetRowRequest getRowRequest = new GetRowRequest(singleRowQueryCriteria);
            return ots.getRow(getRowRequest, null).get();
        } catch (Throwable ex) {
            if (OTSUtil.shouldRetry(ex)) {
                throw new IOException(ex);
            } else {
                throw new DoNotRetryIOException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * if row change has nothing to update, add a dummy column to avoid error in server check
     */
    private RowChange checkRowChange(RowChange rowChange) {
        if (rowChange instanceof RowUpdateChange) {
            RowUpdateChange ruc = (RowUpdateChange) rowChange;
            if (ruc.getColumnsToUpdate().isEmpty()) {
                // nothing to update, add a dummy column to avoid error in server check
                ruc.deleteColumn("dummy&*&&**", 0);
            }
        }
        return rowChange;
    }
}
