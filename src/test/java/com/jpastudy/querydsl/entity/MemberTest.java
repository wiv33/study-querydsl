package com.jpastudy.querydsl.entity;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.List;

import static com.jpastudy.querydsl.entity.QMember.member;
import static com.jpastudy.querydsl.entity.QTeam.team;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberTest {

    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        queryFactory = new JPAQueryFactory(em);

        var teamA = new Team("teamA");
        var teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        var member1 = new Member("member1", 10, teamA);
        var member2 = new Member("member2", 20, teamA);
        var member3 = new Member("member3", 30, teamB);
        var member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void startJPQL() {
        var findByJPQL = em.createQuery("select m from Member m where m.username = :username", Member.class)
                           .setParameter("username", "member1")
                           .getSingleResult();

        assertEquals("member1", findByJPQL.getUsername());
    }

    @Test
    void testStartQuerydsl() {
        var query = new JPAQueryFactory(em);
        var member1 = query.selectFrom(member)
                           .where(member.username.eq("member1"))
                           .fetchOne();

        assertEquals("member1", member1.getUsername());
    }

    @Test
    void testSearch() {
        var member1 = queryFactory.selectFrom(member)
                                  .where(member.username.eq("member1"),
                                         member.age.eq(10))
                                  .fetchOne();
        assertEquals("member1", member1.getUsername());
        assertEquals(10, member1.getAge());

    }

    /**
     * 회원 정렬 순서
     * nulls last
     */
    @Test
    void testSort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        var result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        assertAll(() -> assertEquals("member5", result.get(0).getUsername()),
                  () -> assertEquals("member6", result.get(1).getUsername()),
                  () -> assertNull(result.get(2).getUsername()));

    }

    @Test
    void testPaging() {
        var fetch = queryFactory.selectFrom(member)
                                .orderBy(member.username.desc())
                                .offset(1)
                                .limit(2)
                                .fetch();

        assertEquals(2, fetch.size());
    }

    @Test
    void testPaging2() {
        var fetch = queryFactory.selectFrom(member)
                                .orderBy(member.username.desc())
                                .offset(1)
                                .limit(2)
                                .fetchResults();
        fetch.getResults().forEach(System.out::println);

        assertEquals(4, fetch.getTotal());
        assertEquals(2, fetch.getLimit());
        assertEquals(1, fetch.getOffset());
        assertEquals(2, fetch.getResults().size());
    }

    @Test
    void testAggregation() {
        var result = queryFactory.select(member.count(),
                                        member.age.sum(),
                                        member.age.avg(),
                                        member.age.max(),
                                        member.age.min())
                                .from(member)
                                .fetch();

        var tuple = result.get(0);
        assertEquals(4, tuple.get(member.count()));
        assertEquals(100, tuple.get(member.age.sum()));
        assertEquals(25, tuple.get(member.age.avg()));
        assertEquals(40, tuple.get(member.age.max()));
        assertEquals(10, tuple.get(member.age.min()));
    }


    /**
     * 팀 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    void testGroup() {
        var result = queryFactory.select(team.name, member.age.avg())
                                .from(member)
                                .join(member.team, team)
                                .groupBy(team.name)
                                .fetch();

        var teamA = result.get(0);
        var teamB = result.get(1);

        assertEquals("teamA", teamA.get(team.name));
        assertEquals(15, teamA.get(member.age.avg()));

        assertEquals("teamB", teamB.get(team.name));
        assertEquals(35, teamB.get(member.age.avg()));
    }

    @Test
    void testJoin() {
        var result = queryFactory.select(member)
                                .from(member)
                                .join(member.team, team)
                                .where(team.name.eq("teamA"))
                                .fetch();

        assertEquals("member1", result.get(0).getUsername());
        assertEquals("member2", result.get(1).getUsername());

    }

    @Test
    void testThetaJoin() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        var result = queryFactory.select(member)
                                .from(member, team)
                                .where(member.username.eq(team.name))
                                .fetch();

        assertEquals("teamA", result.get(0).getUsername());
        assertEquals("teamB", result.get(1).getUsername());
    }

    @Test
    void testJoinOn() {
        
    }
}

