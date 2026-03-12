package com.caseflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.caseflow.common.CurrentUserUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                String userId = CurrentUserUtil.getCurrentUserId();
                String displayName = CurrentUserUtil.getCurrentUserDisplayName();
                this.strictInsertFill(metaObject, "createdBy", String.class, userId != null ? userId : "");
                this.strictInsertFill(metaObject, "createdByName", String.class, displayName);
                this.strictInsertFill(metaObject, "updatedBy", String.class, userId != null ? userId : "");
                this.strictInsertFill(metaObject, "updatedByName", String.class, displayName);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                String userId = CurrentUserUtil.getCurrentUserId();
                String displayName = CurrentUserUtil.getCurrentUserDisplayName();
                this.setFieldValByName("updatedBy", userId != null ? userId : "", metaObject);
                this.setFieldValByName("updatedByName", displayName, metaObject);
            }
        };
    }
}
