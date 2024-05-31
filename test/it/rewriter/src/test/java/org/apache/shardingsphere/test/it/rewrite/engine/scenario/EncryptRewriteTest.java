package org.apache.shardingsphere.test.it.rewrite.engine.scenario;

import org.apache.shardingsphere.encrypt.api.config.EncryptRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.config.rule.EncryptColumnRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.config.rule.EncryptTableRuleConfiguration;
import org.apache.shardingsphere.encrypt.rewrite.context.EncryptSQLRewriteContextDecorator;
import org.apache.shardingsphere.encrypt.rule.EncryptRule;
import org.apache.shardingsphere.infra.binder.SQLStatementContextFactory;
import org.apache.shardingsphere.infra.binder.statement.CommonSQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.context.ConnectionContext;
import org.apache.shardingsphere.infra.parser.sql.SQLStatementParserEngine;
import org.apache.shardingsphere.infra.parser.sql.SQLStatementParserEngineFactory;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.sql.impl.DefaultSQLBuilder;
import org.apache.shardingsphere.sql.parser.api.CacheOption;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.junit.Test;

import java.util.*;

public class EncryptRewriteTest {

    @Test
    public void parseThenRewrite() {
        String dbType = "Mysql";
        String schemaName = "test";
        String dbName = "test_db";
//        ShardingSphereSQLParserEngine parserEngine =
//                new ShardingSphereSQLParserEngine(dbType,
//                        new CacheOption(128, 1024),
//                        new CacheOption(128, 1024),
//                        true);

        SQLStatementParserEngine sqlStatementParserEngine = SQLStatementParserEngineFactory.getSQLStatementParserEngine(
                dbType,
                new CacheOption(128, 1024),
                new CacheOption(128, 1024),
                true);
        // SQLStatementParserEngineFactory.
        // SQLStatementContextFactory#newInstance
//        String sql = "select username from t_user where username=?";
//        String sql = "select u1.username from t_user u1, t_user_2  u2 where u1.username = u2.username and  u2.username = ? ";
        String sql = "SELECT u.username, u.pwd FROM t_user u WHERE u.username=(SELECT a.username FROM t_user_2 a WHERE a.pwd=?)";
//        String sql = "select a.username from (select username from t_user where username = ? limit 10 offset 1) a";
        List<Object> params = new ArrayList<>();
        params.add("pwd");

        SQLStatement sqlStatement = sqlStatementParserEngine.parse(sql, true);
        SQLStatementContext<?> sqlStatementContext = SQLStatementContextFactory.newInstance(sqlStatement, params, schemaName);
//        if (sqlStatementContext instanceof ParameterAware) {
//            ((ParameterAware) sqlStatementContext).setUpParameters(testParams.getInputParameters());
//        }

        // 准备改写上下文，生成 sqltokens
//        QueryContext queryContext = new QueryContext(sqlStatementContext, sql, null);
        SQLRewriteContext sqlRewriteContext = new SQLRewriteContext(dbName, new HashMap<>(), sqlStatementContext, sql, params, new ConnectionContext());
        if (((CommonSQLStatementContext<?>) sqlRewriteContext.getSqlStatementContext()).isHintSkipSQLRewrite()) {
            return;
        }

        EncryptRule encryptRule = createEncryptRule();
        new EncryptSQLRewriteContextDecorator().decorate(encryptRule, new ConfigurationProperties(new Properties()), sqlRewriteContext, null);
        sqlRewriteContext.generateSQLTokens();

        // 生成最终sql GenericSQLRewriteEngine? 还是 RouteSQLRewriteEngine? 目前用不到 库表路由，直接使用 generic 就行
        // GenericSQLRewriteEngine 内部处理方式
        String finalSql = new DefaultSQLBuilder(sqlRewriteContext).toSQL();
        System.out.print(finalSql);
        List<Object> parameters = sqlRewriteContext.getParameterBuilder().getParameters();
        System.out.print(" ::: ");
        for (Object parameter : parameters) {
            System.out.print(parameter + " ");
        }


    }

    private EncryptRule createEncryptRule() {
        Properties props = new Properties();
        props.setProperty("aes-key-value", "123456");
        EncryptColumnRuleConfiguration columnConfigAes = new EncryptColumnRuleConfiguration("username", "cipher_username", "", "", "username_plain", "name_encryptor", true);
        EncryptColumnRuleConfiguration columnConfigTest = new EncryptColumnRuleConfiguration("pwd", "cipher_pwd", "assisted_query_pwd", "", "", "pwd_encryptor", true);
        EncryptTableRuleConfiguration encryptTableRuleConfig = new EncryptTableRuleConfiguration("t_user", Arrays.asList(columnConfigAes, columnConfigTest), true);

        EncryptColumnRuleConfiguration columnConfigAes2 = new EncryptColumnRuleConfiguration("username", "cipher_username", "", "", "username_plain", "name_encryptor", true);
        EncryptColumnRuleConfiguration columnConfigTest2 = new EncryptColumnRuleConfiguration("pwd", "cipher_pwd", "", "", "", "pwd_encryptor", true);
        EncryptTableRuleConfiguration encryptTableRuleConfig2 = new EncryptTableRuleConfiguration("t_user_2", Arrays.asList(columnConfigAes2, columnConfigTest2), true);

        Map<String, AlgorithmConfiguration> encryptAlgorithmConfigs = new LinkedHashMap<>(2, 1);
        encryptAlgorithmConfigs.put("name_encryptor", new AlgorithmConfiguration("REWRITE.NORMAL.FIXTURE", props));
        encryptAlgorithmConfigs.put("pwd_encryptor", new AlgorithmConfiguration("REWRITE.NORMAL.FIXTURE", props));

        List<EncryptTableRuleConfiguration> tableRules = new ArrayList<>(2);
        tableRules.add(encryptTableRuleConfig);
        tableRules.add(encryptTableRuleConfig2);
        EncryptRuleConfiguration encryptRuleConfig = new EncryptRuleConfiguration(tableRules, encryptAlgorithmConfigs);
        return new EncryptRule(encryptRuleConfig);
    }

}
