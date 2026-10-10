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

    @org.springframework.data.jpa.repository.Query("SELECT e FROM #{#entityName} e WHERE e.id = ?1")
    java.util.Optional<T> findByIdWithTenant(ID id);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(e) > 0 FROM #{#entityName} e WHERE e.id = ?1")
    boolean existsByIdWithTenant(ID id);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM #{#entityName} e WHERE e.id IN ?1")
    List<T> findAllByIdWithTenant(Iterable<ID> ids);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM #{#entityName} e WHERE e.id = ?1")
    void deleteByIdWithTenant(ID id);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM #{#entityName} e WHERE e.id = ?1")
    T getReferenceByIdWithTenant(ID id);
}
