--[]
-- HSQL can handle several insert statements executed as one statement
insert into t (name) values ('Donald Duck'); 
insert into t (name) values ('Mickey Mouse');
insert into t (name) values ('Marvin the Martian');
--[]
-- Just for example, split the batch in two
insert into t (name) values ('Woody Pride'); 
insert into t (name) values ('Buzz Lightyear');
insert into t (name) values ('Jessica Jane Pride');
