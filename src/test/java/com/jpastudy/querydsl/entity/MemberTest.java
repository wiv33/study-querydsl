package com.jpastudy.querydsl.entity;

import com.jpastudy.querydsl.dto.MemberDto;
import com.jpastudy.querydsl.dto.QMemberDto;
import com.jpastudy.querydsl.dto.UserDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;

import static com.jpastudy.querydsl.entity.QMember.member;
import static com.jpastudy.querydsl.entity.QTeam.team;
import static com.querydsl.jpa.JPAExpressions.select;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberTest {

    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;
    @PersistenceUnit
    EntityManagerFactory emf;

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

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    void testJoinOnFilter() {
        var result = queryFactory.select(member, team)
                                 .from(member)
                                 .leftJoin(member.team, team)
                                 .on(team.name.eq("teamA"))
                                 //                .where(team.name.eq("teamA"))
                                 .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관 관계가 없는 엔티티 외부 조인
     * 회원의 이름과 팀 이름이 같은 대상을 외부 조인
     */
    @Test
    void testJoinOnNoRelation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        var result = queryFactory.select(member, team)
                                 .from(member)
                                 .leftJoin(team).on(member.username.eq(team.name))
                                 .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void testFetchJoinNo() {
        em.flush();
        em.clear();

        var member1 = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assert Objects.nonNull(member1);
        var loaded = emf.getPersistenceUnitUtil().isLoaded(member1.getTeam());
        assertFalse(loaded);

    }

    @Test
    void testFetchJoin() {
        em.flush();
        em.clear();

        var member1 = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        assert Objects.nonNull(member1);
        var loaded = emf.getPersistenceUnitUtil().isLoaded(member1.getTeam());
        assertTrue(loaded);

    }

    /**
     * JPAExpressions
     * <p>
     * 나이가 가장 많은 회원 조회
     */
    @Test
    void testSubQuery() {
        var memberSub = new QMember("memberSub");

        var result = queryFactory.selectFrom(member)
                                 .where(member.age.eq(
                                         select(memberSub.age.max())
                                                 .from(memberSub)))
                                 .fetch();

        assertEquals(40, result.get(0).getAge());
    }

    /**
     * JPAExpressions
     * <p>
     * 나이가 평균 이상인 회원
     */
    @Test
    void testSubQuery2() {
        var memberSub = new QMember("memberSub");

        var result = queryFactory.selectFrom(member)
                                 .where(member.age.goe(
                                         select(memberSub.age.avg())
                                                 .from(memberSub)))
                                 .fetch();

        assertEquals(30, result.get(0).getAge());
        assertEquals(40, result.get(1).getAge());
    }

    /**
     * JPAExpressions
     * <p>
     * 나이가 평균 이상인 회원
     */
    @Test
    void testSubQueryIn() {
        var memberSub = new QMember("memberSub");

        var result = queryFactory.selectFrom(member)
                                 .where(member.age.in(
                                         select(memberSub.age)
                                                 .from(memberSub)
                                                 .where(memberSub.age.gt(10)))
                                       )
                                 .fetch();

        assertEquals(20, result.get(0).getAge());
        assertEquals(30, result.get(1).getAge());
        assertEquals(40, result.get(2).getAge());
    }

    @Test
    void testSelectSubQuery() {
        var memberSub = new QMember("memberSub");
        var result = queryFactory.select(member.username,
                                         select(memberSub.age.avg())
                                                 .from(memberSub))
                                 .from(member)
                                 .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void testBasicCase() {
        var result = queryFactory.select(member.age
                                                 .when(10).then("열 살")
                                                 .when(20).then("스무살")
                                                 .otherwise("기타"))
                                 .from(member)
                                 .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void testComplexCase() {
        var result = queryFactory.select(new CaseBuilder()
                                                 .when(member.age.between(0, 20)).then("0 ~ 20살")
                                                 .when(member.age.between(21, 30)).then("21 ~ 30살")
                                                 .otherwise("기타"))
                                 .from(member)
                                 .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void testConstant() {
        var result = queryFactory.select(member.username, Expressions.constant("A"))
                                 .from(member)
                                 .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void testConcat() {
        var result = queryFactory.select(member.username.concat("_").concat(member.age.stringValue()))
                                 .from(member)
                                 .where(member.username.eq("member1"))
                                 .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void testSimpleProjection() {
        var result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void testTupleProjection() {
        var result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            var name = tuple.get(0, String.class);
            var age = tuple.get(member.age);

            System.out.println("name = " + name);
            System.out.println("age = " + age);
        }
    }

    // 중급 문법


    @Test
    void testFindDtoByJPQL() {
        var query = em.createQuery("select new com.jpastudy.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class);
        var result = query.getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testFindDtoSetter() {
        var result = queryFactory.select(Projections.bean(MemberDto.class,
                                                          member.username, member.age))
                                 .from(member)
                                 .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testFindDtoFields() {
        var result = queryFactory.select(Projections.fields(MemberDto.class,
                                                            member.username, member.age))
                                 .from(member)
                                 .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testFindDtoByConstructor() {
        var result = queryFactory.select(Projections.constructor(MemberDto.class,
                                                                 member.username, member.age))
                                 .from(member)
                                 .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testFindUserDto() {
        var memberSub = new QMember("memberSub");
        var result = queryFactory.select(Projections.fields(UserDto.class,
                                                            member.username.as("name"), ExpressionUtils.as(JPAExpressions
                                                                                                                   .select(memberSub.age.max())
                                                                                                                   .from(memberSub), "age")))
                                 .from(member)
                                 .fetch();

        for (UserDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testFindUserDtoByConstructor() {
        var result = queryFactory.select(Projections.constructor(UserDto.class,
                                                                 member.username, member.age))
                                 .from(member)
                                 .fetch();

        for (UserDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    @Test
    void testFindDtoByQueryProjection() {
        var result = queryFactory.select(new QMemberDto(member.username, member.age))
                                 .from(member)
                                 .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void testDynamicQueryBooleanBuilder() {
        var usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertEquals(1, result.size());
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        var builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory.selectFrom(member)
                           .where(builder)
                           .fetch();
    }

    @Test
    void testDynamicQueryWhereParam() {
        var usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertEquals(1, result.size());
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory.selectFrom(member)
                           .where(
                                   usernameEq(usernameCond),
                                   ageEq(ageCond))
                           .fetch();
    }

    private Predicate usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private Predicate ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    @Test
    void testBuildUpdate() {
        var count = queryFactory.update(member)
                                .set(member.username, "비회원")
                                .where(member.age.lt(28))
                                .execute();

        assertEquals(2, count);

        em.flush();
        em.clear();

        var result = queryFactory.selectFrom(member)
                                 .fetch();
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    void testBulkAdd() {
        var count = queryFactory.update(member)
                                .set(member.age, member.age.add(1))
                                .execute();

        assertEquals(4, count);
    }

    @Test
    void testBulkDelete() {
        var count = queryFactory.delete(member)
                                .where(member.age.gt(18))
                                .execute();
        assertEquals(3, count);
    }

    @Test
    void testFunction() {
        var result = queryFactory.select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                                                                    member.username, "member", "M"))
                                 .from(member)
                                 .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void testFunction2() {
        var result = queryFactory.select(member.username)
                                 .from(member)
//                                 .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                                 .where(member.username.eq(member.username.lower()))
                                 .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}

