package com.jpastudy.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class QuerydslApplicationTests {

	@Autowired
	EntityManager em;

	@Test
	void contextLoads() {
		var hello = new Hello();
		em.persist(hello);

		var query = new JPAQueryFactory(em);
		var qHello = QHello.hello;

		var result = query.selectFrom(qHello)
						  .fetchOne();

		assertEquals(hello, result);
		assertNotNull(result);
		assertEquals(hello.getId(), result.getId());
	}

}
