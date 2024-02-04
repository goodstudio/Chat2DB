package ai.chat2db.plugin.mysql.builder;

import ai.chat2db.plugin.mysql.type.MysqlColumnTypeEnum;
import ai.chat2db.plugin.mysql.type.MysqlIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;


public class MysqlSqlBuilder extends DefaultSqlBuilder implements SqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
        if(StringUtils.isNotBlank(table.getDatabaseName())) {
            script.append("`").append(table.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(table.getName()).append("`").append(" (").append("\n");

        // append column
        for (TableColumn column : table.getColumnList()) {
            if(StringUtils.isBlank(column.getName())|| StringUtils.isBlank(column.getColumnType())){
                continue;
            }
            MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if(StringUtils.isBlank(tableIndex.getName())|| StringUtils.isBlank(tableIndex.getType())){
                continue;
            }
            MysqlIndexTypeEnum mysqlIndexTypeEnum = MysqlIndexTypeEnum.getByType(tableIndex.getType());
            script.append("\t").append("").append(mysqlIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)");


        if (StringUtils.isNotBlank(table.getEngine())) {
            script.append(" ENGINE=").append(table.getEngine());
        }

        if (StringUtils.isNotBlank(table.getCharset())) {
            script.append(" DEFAULT CHARACTER SET=").append(table.getCharset());
        }

        if (StringUtils.isNotBlank(table.getCollate())) {
            script.append(" COLLATE=").append(table.getCollate());
        }

        if (table.getIncrementValue() != null) {
            script.append(" AUTO_INCREMENT=").append(table.getIncrementValue());
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(" COMMENT='").append(table.getComment()).append("'");
        }

        if (StringUtils.isNotBlank(table.getPartition())) {
            script.append(" \n").append(table.getPartition());
        }
        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        script.append("ALTER TABLE ");
        if(StringUtils.isNotBlank(oldTable.getDatabaseName())) {
            script.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(oldTable.getName()).append("`").append("\n");
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("\t").append("RENAME TO ").append("`").append(newTable.getName()).append("`").append(",\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("\t").append("COMMENT=").append("'").append(newTable.getComment()).append("'").append(",\n");
        }
        if (oldTable.getIncrementValue() != newTable.getIncrementValue()) {
            script.append("\t").append("AUTO_INCREMENT=").append(newTable.getIncrementValue()).append(",\n");
        }

        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) &&  StringUtils.isNotBlank(tableColumn.getColumnType())&& StringUtils.isNotBlank(tableColumn.getName())){
                MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(tableColumn.getColumnType());
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                MysqlIndexTypeEnum mysqlIndexTypeEnum = MysqlIndexTypeEnum.getByType(tableIndex.getType());
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
            }
        }

        // append reorder column
        script.append("\t").append(buildGenerateReorderColumnSql(oldTable, newTable));

        if(script.length()>2) {
            script = new StringBuilder(script.substring(0, script.length() - 3));
            script.append(";");
        }

        return script.toString();
    }



    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 14);
        sqlBuilder.append(sql);
        if (offset == 0) {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(pageSize);
        } else {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(offset);
            sqlBuilder.append(",");
            sqlBuilder.append(pageSize);
        }
        return sqlBuilder.toString();
    }




    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE `"+database.getName()+"`");
        if (StringUtils.isNotBlank(database.getCharset())) {
            sqlBuilder.append(" DEFAULT CHARACTER SET=").append(database.getCharset());
        }
        if (StringUtils.isNotBlank(database.getCollation())) {
            sqlBuilder.append(" COLLATE=").append(database.getCollation());
        }
        return sqlBuilder.toString();
    }
    public String buildGenerateReorderColumnSql(Table oldTable, Table newTable) {
        StringBuilder sql = new StringBuilder();
        int n = 0;
        for (int i = 0; i < newTable.getColumnList().size(); i++) {
            TableColumn column = newTable.getColumnList().get(i);
            //String columnName = newTable.getColumnList().get(i).getName();
            // 获取oldTable.getColumnList()中name属性的list
            List<String> oldColumnList = oldTable.getColumnList().stream()
                    .map(TableColumn::getName)
                    .collect(Collectors.toList());

            if (i > oldColumnList.indexOf(column.getOldName())) {
                if (n > 0) {
                    sql.append("ALTER TABLE ");
                    sql.append("`").append(column.getDatabaseName()).append("`").append(".");
                    sql.append("`").append(oldTable.getName()).append("`").append("\n");
                }
                sql.append(" MODIFY COLUMN ");
                MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
                sql.append(typeEnum.buildColumn(column));
                sql.append(" ");
                if (i > 0) {
                    sql.append(" AFTER ");
                    sql.append(newTable.getColumnList().get(i - 1).getName());
                } else {
                    sql.append(" FIRST");
                }
                sql.append(" ;\n");
                n++;
            }

        }
        return sql.toString();
    }
}
