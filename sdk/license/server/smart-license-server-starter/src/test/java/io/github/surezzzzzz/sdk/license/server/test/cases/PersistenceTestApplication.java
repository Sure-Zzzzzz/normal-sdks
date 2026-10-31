package io.github.surezzzzzz.sdk.license.server.test.cases;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * 持久化测试引导：真实 MySQL + 实体建表 + JdbcTemplate。
 *
 * <p>仅测试用：不经 Server 业务装配，专注实体-表映射与乐观锁真实行为。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication(exclude = io.github.surezzzzzz.sdk.license.server.configuration.LicenseServerHttpAutoConfiguration.class)
@EnableJpaRepositories(basePackages = "io.github.surezzzzzz.sdk.license.server.repository")
@EntityScan(basePackages = "io.github.surezzzzzz.sdk.license.server.entity")
@Configuration
public class PersistenceTestApplication {

    /** 与运行宿主一致，DataSource 由官方 MySQL Route 接管；本测试只管理自己的实体结构。 */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("io.github.surezzzzzz.sdk.license.server.entity");
        HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
        vendor.setGenerateDdl(true);
        factory.setJpaVendorAdapter(vendor);
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
        factory.getJpaPropertyMap().put("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        return factory;
    }
}
