--[INSERT_NAME]
insert into t (name) values (?)
--[SELECT_ID]
select id from t where name like @name
--[SELECT_NAME]
select name from t where id = @id
