package com.checador.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;

@NoRepositoryBean
public interface BaseTenantRepository<T, ID> extends Repository<T, ID> {
    T save(T entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
    List<T> findAll();
    void delete(T entity);
    long count();
    void deleteAll(Iterable<? extends T> entities);
    void deleteAll();

    java.util.Optional<T> findByIdAndTenantId(ID id, Long tenantId);

    boolean existsByIdAndTenantId(ID id, Long tenantId);

    List<T> findByIdInAndTenantId(Iterable<ID> ids, Long tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByIdAndTenantId(ID id, Long tenantId);
}
