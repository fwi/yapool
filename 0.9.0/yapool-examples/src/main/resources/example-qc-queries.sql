--[INSERT_NAME]
insert into t (name) values (?)
--[/INSERT_NAME]
--[SELECT_ID]
select id from t where name like @name
--[/SELECT_ID]
--[SELECT_NAME]
select name from t where id = @id
--[/SELECT_NAME]
