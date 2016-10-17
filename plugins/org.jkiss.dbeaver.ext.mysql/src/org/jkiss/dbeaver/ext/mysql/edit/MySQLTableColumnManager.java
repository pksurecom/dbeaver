/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2016 Serge Rieder (serge@jkiss.org)
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.ext.mysql.edit;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.mysql.MySQLConstants;
import org.jkiss.dbeaver.ext.mysql.model.MySQLTableBase;
import org.jkiss.dbeaver.ext.mysql.model.MySQLTableColumn;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.dbeaver.model.impl.edit.DBECommandAbstract;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableColumnManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.utils.CommonUtils;

import java.sql.Types;
import java.util.List;
import java.util.Locale;

/**
 * MySQL table column manager
 */
public class MySQLTableColumnManager extends SQLTableColumnManager<MySQLTableColumn, MySQLTableBase> implements DBEObjectRenamer<MySQLTableColumn>  {

    protected final ColumnModifier<MySQLTableColumn> MySQLDataTypeModifier = new ColumnModifier<MySQLTableColumn>() {
        @Override
        public void appendModifier(MySQLTableColumn column, StringBuilder sql, DBECommandAbstract<MySQLTableColumn> command) {
            sql.append(' ').append(column.getFullTypeName());
        }
    };

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, MySQLTableColumn> getObjectsCache(MySQLTableColumn object)
    {
        return object.getParentObject().getContainer().getTableCache().getChildrenCache(object.getParentObject());
    }

    protected ColumnModifier[] getSupportedModifiers()
    {
        return new ColumnModifier[] {MySQLDataTypeModifier, DefaultModifier, NullNotNullModifier};
    }

    @Override
    public StringBuilder getNestedDeclaration(MySQLTableBase owner, DBECommandAbstract<MySQLTableColumn> command)
    {
        StringBuilder decl = super.getNestedDeclaration(owner, command);
        final MySQLTableColumn column = command.getObject();
        if (!CommonUtils.isEmpty(column.getExtraInfo())) {
            decl.append(" ").append(column.getExtraInfo()); //$NON-NLS-1$
        }
        if (column.isAutoGenerated() &&
            (CommonUtils.isEmpty(column.getExtraInfo()) || !column.getExtraInfo().toLowerCase(Locale.ENGLISH).contains(MySQLConstants.EXTRA_AUTO_INCREMENT)))
        {
            decl.append(" AUTO_INCREMENT"); //$NON-NLS-1$
        }
        if (!CommonUtils.isEmpty(column.getComment())) {
            decl.append(" COMMENT '").append(escapeComment(column.getComment())).append("'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return decl;
    }

    private String escapeComment(String comment) {
        return comment.replace("'", "\\'");
    }

    @Override
    protected MySQLTableColumn createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context, MySQLTableBase parent, Object copyFrom)
    {
        DBSDataType columnType = findBestDataType(parent.getDataSource(), "varchar"); //$NON-NLS-1$

        final MySQLTableColumn column = new MySQLTableColumn(parent);
        column.setName(getNewColumnName(monitor, context, parent));
        final String typeName = columnType == null ? "integer" : columnType.getName().toLowerCase();
        column.setTypeName(typeName); //$NON-NLS-1$
        column.setMaxLength(columnType != null && columnType.getDataKind() == DBPDataKind.STRING ? 100 : 0);
        column.setValueType(columnType == null ? Types.INTEGER : columnType.getTypeID());
        column.setOrdinalPosition(-1);
        column.setFullTypeName(column.getFullTypeName());
        return column;
    }

    @Override
    protected void addObjectModifyActions(List<DBEPersistAction> actionList, ObjectChangeCommand command)
    {
        final MySQLTableColumn column = command.getObject();

        actionList.add(
            new SQLDatabasePersistAction(
                "Modify column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " MODIFY COLUMN " + getNestedDeclaration(column.getTable(), command))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void renameObject(DBECommandContext commandContext, MySQLTableColumn object, String newName) throws DBException {
        processObjectRename(commandContext, object, newName);
    }

    @Override
    protected void addObjectRenameActions(List<DBEPersistAction> actions, ObjectRenameCommand command)
    {
        final MySQLTableColumn column = command.getObject();

        actions.add(
            new SQLDatabasePersistAction(
                "Rename column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " CHANGE " +
                    DBUtils.getQuotedIdentifier(column.getDataSource(), command.getOldName()) + " " +
                    getNestedDeclaration(column.getTable(), command)));
    }

}
