package com.alicloud.tablestore.hbase;

import com.alicloud.openservices.tablestore.core.utils.Preconditions;
import com.alicloud.tablestore.adaptor.client.OTSAdapter;
import com.alicloud.tablestore.adaptor.client.TablestoreClientConf;
import com.alicloud.tablestore.adaptor.struct.OTableDescriptor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ServerType;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.replication.ReplicationPeerDescription;
// import org.apache.hadoop.hbase.ProcedureInfo;
import org.apache.hadoop.hbase.client.CompactType;
import org.apache.hadoop.hbase.client.CompactionState;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.LogEntry;
import org.apache.hadoop.hbase.client.SnapshotDescription;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorUtils;
import org.apache.hadoop.hbase.client.security.SecurityCapability;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.protobuf.generated.AccessControlProtos.GetUserPermissionsRequest;
import org.apache.hadoop.hbase.protobuf.generated.AccessControlProtos.UserPermission;
import org.apache.hadoop.hbase.quotas.QuotaFilter;
import org.apache.hadoop.hbase.quotas.QuotaRetriever;
import org.apache.hadoop.hbase.quotas.QuotaSettings;
import org.apache.hadoop.hbase.quotas.SpaceQuotaSnapshotView;
import org.apache.hadoop.hbase.security.access.Permission;
import org.apache.hadoop.hbase.snapshot.HBaseSnapshotException;
import org.apache.hadoop.hbase.snapshot.RestoreSnapshotException;
import org.apache.hadoop.hbase.snapshot.SnapshotCreationException;
import org.apache.hadoop.hbase.snapshot.UnknownSnapshotException;
import org.apache.hadoop.hbase.util.Pair;
// import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

public class TablestoreAdmin implements Admin {
    private static final Map<Configuration, TablestoreClientConf> globalTablestoreConfs = new HashMap<Configuration, TablestoreClientConf>();

    private static final Logger log = LoggerFactory.getLogger(TablestoreAdmin.class);

    private final Set<TableName> disabledTables;
    private final TablestoreConnection connection;
    private OTSAdapter tablestoreAdaptor;

    public TablestoreAdmin(TablestoreConnection connection) {
        TablestoreClientConf tablestoreConf = connection.getTablestoreConf();
        this.disabledTables = new CopyOnWriteArraySet<TableName>();
        this.tablestoreAdaptor = OTSAdapter.getInstance(tablestoreConf);
        this.connection = connection;
    }

    @Override
    public int getOperationTimeout() {
        throw new UnsupportedOperationException("getOperationTimeout");
    }

    @Override
    public void abort(String why, Throwable e) {
        throw new UnsupportedOperationException("abort");
    }

    @Override
    public boolean isAborted() {
        throw new UnsupportedOperationException("isAborted");
    }

    @Override
    public Connection getConnection() {
        return this.connection;
    }

    @Override
    public boolean tableExists(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);
        return this.tablestoreAdaptor.listTable().contains(tableName.getNameAsString());
    }

    @Override
    public List<TableDescriptor> listTableDescriptors() throws IOException {
        List<String> tables = this.tablestoreAdaptor.listTable();
        List<TableDescriptor> tableDescriptors = new ArrayList<TableDescriptor>();
        for (int i = 0; i < tables.size(); i++) {
            tableDescriptors.add(getDescriptor(TableName.valueOf(tables.get(i))));
        }

        return tableDescriptors;
    }

    @Override
    public List<TableDescriptor> listTableDescriptors(Pattern pattern, boolean includeSysTables) throws IOException {
        Preconditions.checkNotNull(pattern);

        return this.listTableDescriptors(pattern);
    }

    @Override
    public List<TableDescriptor> listTableDescriptors(List<TableName> tableNames) throws IOException {
        return this.listTableDescriptors();
    }

    @Override
    public List<TableDescriptor> listTableDescriptors(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        List<String> tables = this.tablestoreAdaptor.listTable();
        List<TableDescriptor> tableDescriptors = new ArrayList<TableDescriptor>();
        for (int i = 0; i < tables.size(); i++) {
            if (pattern.matcher(tables.get(i)).matches()) {
                tableDescriptors.add(getTableDescriptor(TableName.valueOf(tables.get(i))));
            }
        }

        return tableDescriptors;
    }

    @Override
    public HTableDescriptor[] listTables() throws IOException {
        List<String> tables = this.tablestoreAdaptor.listTable();
        HTableDescriptor[] tableDescriptors = new HTableDescriptor[tables.size()];
        for (int i = 0; i < tables.size(); i++) {
            tableDescriptors[i] = getTableDescriptor(TableName.valueOf(tables.get(i)));
        }
        return tableDescriptors;
    }

    @Override
    public HTableDescriptor[] listTables(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        List<String> tables = this.tablestoreAdaptor.listTable();
        List<HTableDescriptor> tableDescriptors = new ArrayList<HTableDescriptor>();
        for (int i = 0; i < tables.size(); i++) {
            if (pattern.matcher(tables.get(i)).matches()) {
                tableDescriptors.add(getTableDescriptor(TableName.valueOf(tables.get(i))));
            }
        }

        HTableDescriptor[] hTableDescriptors = new HTableDescriptor[tableDescriptors.size()];
        return tableDescriptors.toArray(hTableDescriptors);
    }

    @Override
    public HTableDescriptor[] listTables(String regex) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        return listTables(pattern);
    }

    @Override
    public HTableDescriptor[] listTables(Pattern pattern, boolean includeSysTables) throws IOException {
        Preconditions.checkNotNull(pattern);

        return listTables(pattern);
    }

    @Override
    public HTableDescriptor[] listTables(String regex, boolean includeSysTables) throws IOException {
        return listTables(regex);
    }

    @Override
    public TableName[] listTableNames() throws IOException {
        List<String> tables = this.tablestoreAdaptor.listTable();
        TableName[] tableNames = new TableName[tables.size()];
        for (int i = 0; i < tables.size(); i++) {
            tableNames[i] = TableName.valueOf(tables.get(i));
        }
        return tableNames;
    }

    @Override
    public TableName[] listTableNames(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        List<String> tables = this.tablestoreAdaptor.listTable();
        List<TableName> tableNames = new ArrayList<TableName>();
        for (int i = 0; i < tables.size(); i++) {
            if (pattern.matcher(tables.get(i)).matches()) {
                tableNames.add(TableName.valueOf(tables.get(i)));
            }
        }
        TableName[] tableNames1 = new TableName[tableNames.size()];
        return tableNames.toArray(tableNames1);
    }

    @Override
    public TableName[] listTableNames(String regex) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        return listTableNames(pattern);
    }

    @Override
    public TableName[] listTableNames(Pattern pattern, boolean includeSysTables) throws IOException {
        Preconditions.checkNotNull(pattern);

        return listTableNames(pattern);
    }

    @Override
    public TableName[] listTableNames(String regex, boolean includeSysTables) throws IOException {
        return listTableNames(regex);
    }

    /*
     * this will replace HTableDescriptor getTableDescriptor(TableName tableName)
     */
    public TableDescriptor getDescriptor(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        OTableDescriptor oTableDescriptor =  this.tablestoreAdaptor.describeTable(tableName.getNameAsString());
        ColumnMapping columnMapping = new ColumnMapping(tableName.getNameAsString(), this.connection.getConfiguration());
        return ElementConvertor.toHbaseTableDescriptor(oTableDescriptor, columnMapping);
    }

    @Override
    public HTableDescriptor getTableDescriptor(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        OTableDescriptor oTableDescriptor =  this.tablestoreAdaptor.describeTable(tableName.getNameAsString());

        ColumnMapping columnMapping = new ColumnMapping(tableName.getNameAsString(), this.connection.getConfiguration());
        return ElementConvertor.toHbaseTableDescriptor(oTableDescriptor, columnMapping);
    }

    @Override
    public void createTable(TableDescriptor desc) throws IOException {
        Preconditions.checkNotNull(desc);

        Set<byte[]> familiesKeys = desc.getColumnFamilyNames();
        if (familiesKeys.size() > 1) {
            throw new UnsupportedOperationException("Only support one family");
        }

        int maxVersion = 1;
        int ttl = Integer.MAX_VALUE;
        if (familiesKeys.size() == 1) {
            ColumnFamilyDescriptor descriptor = desc.getColumnFamily(familiesKeys.iterator().next());
            if (descriptor.getMaxVersions() > 0) {
                maxVersion = descriptor.getMaxVersions();
            }

            ttl = descriptor.getTimeToLive();
        }
        OTableDescriptor tableDescriptor = new OTableDescriptor(desc.getTableName().getNameAsString(), maxVersion, ttl);

        this.tablestoreAdaptor.createTable(tableDescriptor);
    }

    @Override
    public void createTable(TableDescriptor desc, byte[] startKey, byte[] endKey, int numRegions) throws IOException {
        throw new UnsupportedOperationException("createTable(HTableDescriptor desc, byte[] startKey, byte[] endKey, int numRegions)");
    }

    @Override
    public void createTable(TableDescriptor desc, byte[][] splitKeys) throws IOException {
        throw new UnsupportedOperationException("createTable(HTableDescriptor desc, byte[][] splitKeys)");
    }

    @Override
    public Future<Void> createTableAsync(TableDescriptor desc) throws IOException {
        throw new UnsupportedOperationException("createTableAsync(HTableDescriptor desc, byte[][] splitKeys)");
    }

    @Override
    public Future<Void> createTableAsync(TableDescriptor desc, byte[][] splitKeys) throws IOException {
        throw new UnsupportedOperationException("createTableAsync(HTableDescriptor desc, byte[][] splitKeys)");
    }

    @Override
    public boolean isRpcThrottleEnabled() throws IOException {
        throw new UnsupportedOperationException("isRpcThrottleEnabled");
    }

    @Override
    public boolean switchRpcThrottle(boolean enable) throws IOException {
        throw new UnsupportedOperationException("switchRpcThrottle");
    }

    @Override
    public void deleteTable(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        this.tablestoreAdaptor.deleteTable(tableName.getNameAsString());
    }

    @Override
    public HTableDescriptor[] deleteTables(String regex) throws IOException {
        HTableDescriptor[] tables = listTables(regex);
        for (HTableDescriptor tableName : tables) {
            deleteTable(tableName.getTableName());
        }
        return tables;
    }

    @Override
    public HTableDescriptor[] deleteTables(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        HTableDescriptor[] tables = listTables(pattern);
        for (HTableDescriptor tableName : tables) {
            deleteTable(tableName.getTableName());
        }
        return tables;
    }

    @Override
    public void truncateTable(TableName tableName, boolean preserveSplits) throws IOException {
        Preconditions.checkNotNull(tableName);

        HTableDescriptor descriptor = getTableDescriptor(tableName);
        deleteTable(descriptor.getTableName());
        createTable(descriptor);
    }

    @Override
    public void enableTable(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        if (!this.tableExists(tableName)) {
            throw new TableNotFoundException(tableName);
        }
        disabledTables.remove(tableName);
    }

    @Override
    public Future<Void> enableTableAsync(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        this.enableTable(tableName);
        return null;
    }

    @Override
    public HTableDescriptor[] enableTables(String regex) throws IOException {
        HTableDescriptor[] tableDescriptors = this.listTables(regex);
        for (HTableDescriptor tableDescriptor : tableDescriptors) {
            this.enableTable(tableDescriptor.getTableName());
        }
        return tableDescriptors;
    }

    @Override
    public HTableDescriptor[] enableTables(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        HTableDescriptor[] tableDescriptors = this.listTables(pattern);
        for (HTableDescriptor tableDescriptor : tableDescriptors) {
            this.enableTable(tableDescriptor.getTableName());
        }
        return tableDescriptors;
    }

    @Override
    public Future<Void> disableTableAsync(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        disableTable(tableName);
        return null;
    }

    @Override
    public Map<TableName, Long> getSpaceQuotaTableSizes() throws IOException {
        throw new UnsupportedOperationException("getSpaceQuotaTableSizes");
    }

    @Override
    public boolean exceedThrottleQuotaSwitch(boolean enable) throws IOException {
        throw new UnsupportedOperationException("exceedThrottleQuotaSwitch");
    }

    @Override
    public SpaceQuotaSnapshotView getCurrentSpaceQuotaSnapshot(String namespace) throws IOException {
        throw new UnsupportedOperationException("getCurrentSpaceQuotaSnapshot");
    }

    @Override
    public SpaceQuotaSnapshotView getCurrentSpaceQuotaSnapshot(TableName tableName) throws IOException {
        throw new UnsupportedOperationException("getCurrentSpaceQuotaSnapshot");
    }

    @Override
    public Map<TableName,? extends SpaceQuotaSnapshotView> getRegionServerSpaceQuotaSnapshots(ServerName serverName) throws IOException {
        throw new UnsupportedOperationException("getRegionServerSpaceQuotaSnapshots");
    }

    @Override
    public void disableTable(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        if (!this.tableExists(tableName)) {
            throw new TableNotFoundException(tableName);
        }
        if (this.isTableDisabled(tableName)) {
            throw new TableNotEnabledException(tableName);
        }
        disabledTables.add(tableName);
    }

    @Override
    public HTableDescriptor[] disableTables(String regex) throws IOException {
        HTableDescriptor[] tableDescriptors = this.listTables(regex);
        for (HTableDescriptor tableDescriptor : tableDescriptors) {
            this.disableTable(tableDescriptor.getTableName());
        }
        return tableDescriptors;
    }

    @Override
    public HTableDescriptor[] disableTables(Pattern pattern) throws IOException {
        Preconditions.checkNotNull(pattern);

        HTableDescriptor[] tableDescriptors = this.listTables(pattern);
        for (HTableDescriptor tableDescriptor : tableDescriptors) {
            this.disableTable(tableDescriptor.getTableName());
        }
        return tableDescriptors;
    }

    @Override
    public boolean isTableEnabled(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        return !isTableDisabled(tableName);
    }

    @Override
    public boolean isTableDisabled(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        return disabledTables.contains(tableName);
    }

    @Override
    public boolean isTableAvailable(TableName tableName) throws IOException {
        Preconditions.checkNotNull(tableName);

        return tableExists(tableName);
    }

    @Override
    public boolean isTableAvailable(TableName tableName, byte[][] splitKeys) throws IOException {
        Preconditions.checkNotNull(tableName);

        return isTableAvailable(tableName);
    }

    @Override
    public Pair<Integer, Integer> getAlterStatus(TableName tableName) throws IOException {
        return new Pair<Integer, Integer>(0, 0);
    }

    @Override
    public Pair<Integer, Integer> getAlterStatus(byte[] tableName) throws IOException {
        return new Pair<Integer, Integer>(0, 0);
    }

    // @Override
    // public void addColumn(TableName tableName, HColumnDescriptor column) throws IOException {
    //     throw new UnsupportedOperationException("addColumn");
    // }
    @Override
    public void addColumnFamily(TableName tableName, ColumnFamilyDescriptor columnFamily) throws IOException {
        throw new UnsupportedOperationException("addColumnFamily");
    }

    @Override
    public void deleteColumn(TableName tableName, byte[] columnName) throws IOException {
        throw new UnsupportedOperationException("deleteColumn");
    }

    @Override
    public void modifyColumnFamily(TableName tableName, ColumnFamilyDescriptor descriptor) throws IOException {
        Preconditions.checkNotNull(tableName);

        int maxVersion = descriptor.getMaxVersions();
        int ttl = descriptor.getTimeToLive();
        OTableDescriptor tableDescriptor = new OTableDescriptor(tableName.getNameAsString(), maxVersion, ttl);

        this.tablestoreAdaptor.updateTable(tableDescriptor);
    }

    // @Override
    // public void modifyColumn(TableName tableName, HColumnDescriptor descriptor) throws IOException {
    //     Preconditions.checkNotNull(tableName);

    //     int maxVersion = descriptor.getMaxVersions();
    //     int ttl = descriptor.getTimeToLive();
    //     OTableDescriptor tableDescriptor = new OTableDescriptor(tableName.getNameAsString(), maxVersion, ttl);

    //     this.tablestoreAdaptor.updateTable(tableDescriptor);
    // }

    @Override
    public void closeRegion(String regionname, String serverName) throws IOException {
        throw new UnsupportedOperationException("closeRegion");
    }

    @Override
    public void closeRegion(byte[] regionname, String serverName) throws IOException {
        throw new UnsupportedOperationException("closeRegion");
    }

    @Override
    public boolean closeRegionWithEncodedRegionName(String encodedRegionName, String serverName) throws IOException {
        throw new UnsupportedOperationException("closeRegion");
    }

    @Override
    public void closeRegion(ServerName sn, HRegionInfo hri) throws IOException {
        throw new UnsupportedOperationException("closeRegion");
    }

    @Override
    public List<HRegionInfo> getOnlineRegions(ServerName sn) throws IOException {
        throw new UnsupportedOperationException("getOnlineRegions");
    }


    @Override
    public List<LogEntry> getLogEntries(Set<ServerName> serverNames,
                             String logType,
                             ServerType serverType,
                             int limit,
                             Map<String,Object> filterParams
    ) throws IOException {
        throw new UnsupportedOperationException("getLogEntries");
    }

    @Override
    public List<Boolean> clearSlowLogResponses(Set<ServerName> serverNames) throws IOException {
        throw new UnsupportedOperationException("clearSlowLogResponses");
    }

    @Override
    public void flushMasterStore() throws IOException {
        log.info("flushMasterStore is a no-op");
    }

    @Override
    public void flush(TableName tableName) throws IOException {
        log.info("flush is a no-op");
    }

    @Override
    public void flushRegion(byte[] regionName) throws IOException {
        log.info("flushRegion is a no-op");
    }

    @Override
    public void compact(TableName tableName) throws IOException {
        log.info("compact is a no-op");
    }

    @Override
    public void compactRegion(byte[] regionName) throws IOException {
        log.info("compactRegion is a no-op");
    }

    @Override
    public void compact(TableName tableName, byte[] columnFamily) throws IOException {
        log.info("compact is a no-op");
    }

    @Override
    public void compactRegion(byte[] regionName, byte[] columnFamily) throws IOException {
        log.info("compactRegion is a no-op");
    }

    @Override
    public void majorCompact(TableName tableName) throws IOException {
        log.info("majorCompact is a no-op");
    }

    @Override
    public void majorCompactRegion(byte[] regionName) throws IOException {
        log.info("majorCompactRegion is a no-op");
    }

    @Override
    public void majorCompact(TableName tableName, byte[] columnFamily) throws IOException {
        log.info("majorCompact is a no-op");
    }

    @Override
    public void majorCompactRegion(byte[] regionName, byte[] columnFamily) throws IOException {
        log.info("majorCompactRegion is a no-op");
    }

    @Override
    public void compactRegionServer(ServerName sn, boolean major) throws IOException, InterruptedException {
        log.info("compactRegionServer is a no-op");
    }

    @Override
    public void move(byte[] encodedRegionName, byte[] destServerName) throws IOException {
        log.info("move is a no-op");
    }

    @Override
    public void assign(byte[] regionName) throws IOException {
        log.info("assign is a no-op");
    }

    @Override
    public void unassign(byte[] regionName, boolean force) throws IOException {
        log.info("unassign is a no-op");
    }

    @Override
    public void offline(byte[] regionName) throws IOException {
        throw new UnsupportedOperationException("offline");
    }

    @Override
    public boolean setBalancerRunning(boolean on, boolean synchronous) throws IOException {
        return true;
    }

    @Override
    public boolean balancer() throws IOException {
        log.info("balancer is a no-op");
        return true;
    }

    @Override
    public boolean isBalancerEnabled() throws IOException {
        return false;
    }

    @Override
    public List<ReplicationPeerDescription> listReplicationPeers() throws IOException {
        
    }

    @Override
    public boolean normalize() throws IOException {
        throw new UnsupportedOperationException("normalize");
    }

    @Override
    public boolean normalizerSwitch(boolean on) throws IOException {
        throw new UnsupportedOperationException("normalizerSwitch");
    }

    @Override
    public boolean isNormalizerEnabled() throws IOException {
        return false;
    }

    @Override
    public boolean setNormalizerRunning(boolean on) throws IOException {
        throw new UnsupportedOperationException("setNormalizerRunning");
    }

    @Override
    public boolean enableCatalogJanitor(boolean enable) throws IOException {
        throw new UnsupportedOperationException("enableCatalogJanitor");
    }

    @Override
    public int runCatalogJanitor() throws IOException {
        throw new UnsupportedOperationException("runCatalogJanitor");
    }

    @Override
    public boolean isCatalogJanitorEnabled() throws IOException {
        throw new UnsupportedOperationException("isCatalogJanitorEnabled");
    }

    @Override
    public void mergeRegions(byte[] nameOfRegionA, byte[] nameOfRegionB, boolean forcible) throws IOException {
        log.info("mergeRegions is a no-op");
    }

    @Override
    public void split(TableName tableName) throws IOException {
        log.info("split is a no-op");
    }

    @Override
    public void splitRegion(byte[] regionName) throws IOException {
        log.info("splitRegion is a no-op");
    }

    @Override
    public void split(TableName tableName, byte[] splitPoint) throws IOException {
        log.info("split is a no-op");
    }

    @Override
    public void splitRegion(byte[] regionName, byte[] splitPoint) throws IOException {
        log.info("splitRegion is a no-op");
    }

    @Override
    public void modifyTable(TableName tableName, TableDescriptor htd) throws IOException {
        throw new UnsupportedOperationException("modifyTable");
    }

    @Override
    public Future<Void> modifyTableAsync(TableDescriptor td) throws IOException {
        throw new UnsupportedOperationException("modifyTableAsync");
    }

    @Override
    public Future<Void> modifyTableAsync(TableDescriptor td, boolean bool) throws IOException {
        throw new UnsupportedOperationException("modifyTableAsync");
    }

    @Override
    public Future<Void> modifyTableAsync(TableName tableName, TableDescriptor td) throws IOException {
        throw new UnsupportedOperationException("modifyTableAsync");
    }

    @Override
    public void shutdown() throws IOException {
        throw new UnsupportedOperationException("shutdown");
    }

    @Override
    public void stopMaster() throws IOException {
        throw new UnsupportedOperationException("stopMaster");
    }

    @Override
    public void stopRegionServer(String hostnamePort) throws IOException {
        throw new UnsupportedOperationException("stopRegionServer");
    }

    @Override
    public ClusterStatus getClusterStatus() throws IOException {
        throw new UnsupportedOperationException("getClusterStatus");
    }

    @Override
    public Configuration getConfiguration() {
        return connection.getConfiguration();
    }

    @Override
    public void createNamespace(NamespaceDescriptor descriptor) throws IOException {
        throw new UnsupportedOperationException("createNamespace");
    }

    @Override
    public void modifyNamespace(NamespaceDescriptor descriptor) throws IOException {
        throw new UnsupportedOperationException("modifyNamespace");
    }

    @Override
    public void deleteNamespace(String name) throws IOException {
        throw new UnsupportedOperationException("deleteNamespace");
    }

    @Override
    public NamespaceDescriptor getNamespaceDescriptor(String name) throws IOException {
        throw new UnsupportedOperationException("getNamespaceDescriptor");
    }

    @Override
    public NamespaceDescriptor[] listNamespaceDescriptors() throws IOException {
        throw new UnsupportedOperationException("listNamespaceDescriptors");
    }

    @Override
    public List<TableDescriptor> listTableDescriptorsByNamespace(byte[] name) throws IOException {
        throw new UnsupportedOperationException("listTableDescriptorsByNamespace");
    }

    @Override
    public HTableDescriptor[] listTableDescriptorsByNamespace(String name) throws IOException {
        throw new UnsupportedOperationException("listTableDescriptorsByNamespace");
    }

    @Override
    public TableName[] listTableNamesByNamespace(String name) throws IOException {
        throw new UnsupportedOperationException("listTableNamesByNamespace");
    }

    @Override
    public List<HRegionInfo> getTableRegions(TableName tableName) throws IOException {
        throw new UnsupportedOperationException("getTableRegions");
    }

    @Override
    public void close() throws IOException {
        if (this.tablestoreAdaptor != null) {
            this.tablestoreAdaptor.close();
            this.tablestoreAdaptor = null;
        }
    }

    @Override
    public HTableDescriptor[] getTableDescriptorsByTableName(List<TableName> tableNames) throws IOException {
        Preconditions.checkNotNull(tableNames);

        HTableDescriptor[] tableDescriptors = new HTableDescriptor[tableNames.size()];
        for (int i = 0; i < tableNames.size(); i++) {
            TableName tableName = tableNames.get(i);
            Preconditions.checkNotNull(tableName);
            tableDescriptors[i] = getTableDescriptor(tableName);
        }
        return tableDescriptors;
    }

    @Override
    public HTableDescriptor[] getTableDescriptors(List<String> names) throws IOException {
        HTableDescriptor[] tableDescriptors = new HTableDescriptor[names.size()];
        for (int i = 0; i < names.size(); i++) {
            tableDescriptors[i] = getTableDescriptor(TableName.valueOf(names.get(i)));
        }
        return tableDescriptors;
    }

    @Override
    public List<Boolean> hasUserPermissions(String userName, List<Permission> permissions) throws IOException {
        throw new UnsupportedOperationException("hasUserPermissions");
    }

    @Override
    public List<org.apache.hadoop.hbase.security.access.UserPermission> getUserPermissions(org.apache.hadoop.hbase.security.access.GetUserPermissionsRequest getUserPermissionsRequest) throws IOException {
        throw new UnsupportedOperationException("getUserPermissions");
    }

    @Override
    public void revoke(org.apache.hadoop.hbase.security.access.UserPermission userPermission) throws IOException {
        throw new UnsupportedOperationException("revoke");
    }

    @Override
    public void grant(org.apache.hadoop.hbase.security.access.UserPermission userPermission, boolean mergeExistingPermissions) throws IOException {
        throw new UnsupportedOperationException("grant");
    }

    @Override
    public boolean abortProcedure(long procId, boolean mayInterruptIfRunning) throws IOException {
        throw new UnsupportedOperationException("abortProcedure");
    }

    // @Override
    // public ProcedureInfo[] listProcedures() throws IOException {
    //     throw new UnsupportedOperationException("listProcedures");
    // }

    @Override
    public Future<Boolean> abortProcedureAsync(long procId, boolean mayInterruptIfRunning) throws IOException {
        throw new UnsupportedOperationException("abortProcedureAsync");
    }

    @Override
    public void rollWALWriter(ServerName serverName) throws IOException {
        throw new UnsupportedOperationException("rollWALWriter");
    }

    @Override
    public String[] getMasterCoprocessors() throws IOException {
        throw new UnsupportedOperationException("getMasterCoprocessors");
    }

    @Override
    public CompactionState getCompactionState(TableName tableName) throws IOException {
        throw new UnsupportedOperationException("getCompactionState");
    }

    @Override
    public CompactionState getCompactionState(TableName tableName, CompactType compactType) throws IOException {
        throw new UnsupportedOperationException("getCompactionState");
    }

    @Override
    public CompactionState getCompactionStateForRegion(byte[] regionName) throws IOException {
        throw new UnsupportedOperationException("getCompactionStateForRegion");
    }

    @Override
    public long getLastMajorCompactionTimestamp(TableName tableName) throws IOException {
        throw new UnsupportedOperationException("getLastMajorCompactionTimestamp");
    }

    @Override
    public long getLastMajorCompactionTimestampForRegion(byte[] regionName) throws IOException {
        throw new UnsupportedOperationException("getLastMajorCompactionTimestampForRegion");
    }

    @Override
    public boolean isSnapshotCleanupEnabled() throws IOException {
        throw new UnsupportedOperationException("isSnapshotCleanupEnabled");
    }

    @Override
    public boolean snapshotCleanupSwitch(boolean on, boolean asynchronous) throws IOException {
        throw new UnsupportedOperationException("snapshotCleanupSwitch");
    }

    @Override
    public void snapshot(String snapshotName, TableName tableName) throws IOException, IllegalArgumentException {
        throw new UnsupportedOperationException("snapshot");
    }

    @Override
    public void snapshot(byte[] snapshotName, TableName tableName) throws IOException, IllegalArgumentException {
        throw new UnsupportedOperationException("snapshot");
    }

    // @Override
    // public void snapshot(String snapshotName, TableName tableName, SnapshotDescription.Type type) throws IOException, IllegalArgumentException {
    //     throw new UnsupportedOperationException("snapshot");
    // }

    @Override
    public void snapshot(SnapshotDescription snapshot) throws IOException, SnapshotCreationException, IllegalArgumentException {
        throw new UnsupportedOperationException("snapshot");
    }

    @Override
    public Future<Void> snapshotAsync(SnapshotDescription snapshot) throws IOException {
        throw new UnsupportedOperationException("snapshotAsync");
    }

    @Override
    public boolean isSnapshotFinished(SnapshotDescription snapshot) throws IOException, HBaseSnapshotException, UnknownSnapshotException {
        throw new UnsupportedOperationException("isSnapshotFinished");
    }

    @Override
    public void restoreSnapshot(byte[] snapshotName) throws IOException {
        throw new UnsupportedOperationException("restoreSnapshot");
    }

    @Override
    public void restoreSnapshot(String snapshotName) throws IOException, RestoreSnapshotException {
        throw new UnsupportedOperationException("restoreSnapshot");
    }

    @Override
    public void restoreSnapshot(String snapshotName, boolean takeFailSafeSnapshot, boolean restoreAcl) throws IOException, RestoreSnapshotException {
        throw new UnsupportedOperationException("restoreSnapshot");
    }

    @Override
    public void restoreSnapshot(byte[] snapshotName, boolean takeFailSafeSnapshot) throws IOException {
        throw new UnsupportedOperationException("restoreSnapshot");
    }

    @Override
    public void restoreSnapshot(String snapshotName, boolean takeFailSafeSnapshot) throws IOException {
        throw new UnsupportedOperationException("restoreSnapshot");
    }

    @Override
    public void cloneTableSchema(TableName tableName, TableName newTableName, boolean preserveSplits) throws IOException {
        throw new UnsupportedOperationException("cloneTableSchema");
    }

    @Override
    public void cloneSnapshot(byte[] snapshotName, TableName tableName) throws IOException {
        throw new UnsupportedOperationException("cloneSnapshot");
    }

    @Override
    public void cloneSnapshot(String snapshotName, TableName tableName) throws IOException {
        throw new UnsupportedOperationException("cloneSnapshot");
    }

    @Override
    public void execProcedure(String signature, String instance, Map<String, String> props) throws IOException {
        throw new UnsupportedOperationException("execProcedure");
    }

    @Override
    public byte[] execProcedureWithRet(String signature, String instance, Map<String, String> props) throws IOException {
        throw new UnsupportedOperationException("execProcedureWithRet");
    }

    @Override
    public boolean isProcedureFinished(String signature, String instance, Map<String, String> props) throws IOException {
        throw new UnsupportedOperationException("isProcedureFinished");
    }

    @Override
    public List<SnapshotDescription> listSnapshots() throws IOException {
        throw new UnsupportedOperationException("listSnapshots");
    }

    /*
     * deprecated
     */
    // @Override
    // public List<SnapshotDescription> listSnapshots(String regex) throws IOException {
    //     throw new UnsupportedOperationException("listSnapshots");
    // }

    @Override
    public List<SnapshotDescription> listSnapshots(Pattern pattern) throws IOException {
        throw new UnsupportedOperationException("listSnapshots");
    }

    @Override
    public void deleteSnapshot(byte[] snapshotName) throws IOException {
        throw new UnsupportedOperationException("deleteSnapshot");
    }

    @Override
    public void deleteSnapshot(String snapshotName) throws IOException {
        throw new UnsupportedOperationException("deleteSnapshot");
    }

    @Override
    public void deleteSnapshots(String regex) throws IOException {
        throw new UnsupportedOperationException("deleteSnapshots");
    }

    @Override
    public void deleteSnapshots(Pattern pattern) throws IOException {
        throw new UnsupportedOperationException("deleteSnapshot");
    }

    @Override
    public void setQuota(QuotaSettings quota) throws IOException {
        throw new UnsupportedOperationException("setQuota");
    }

    @Override
    public QuotaRetriever getQuotaRetriever(QuotaFilter filter) throws IOException {
        throw new UnsupportedOperationException("getQuotaRetriever");
    }

    @Override
    public CoprocessorRpcChannel coprocessorService() {
        throw new UnsupportedOperationException("coprocessorService");
    }

    @Override
    public CoprocessorRpcChannel coprocessorService(ServerName sn) {
        throw new UnsupportedOperationException("coprocessorService");
    }

    @Override
    public void updateConfiguration(ServerName server) throws IOException {
        throw new UnsupportedOperationException("updateConfiguration");
    }

    @Override
    public void updateConfiguration() throws IOException {
        throw new UnsupportedOperationException("updateConfiguration");
    }

    @Override
    public int getMasterInfoPort() throws IOException {
        throw new UnsupportedOperationException("getMasterInfoPort");
    }

    @Override
    public List<SecurityCapability> getSecurityCapabilities() throws IOException {
        throw new UnsupportedOperationException("getSecurityCapabilities");
    }
}
