# perekupi
Code for work in course 'Project managment'

# DB connection:
terminal command: psql -h dpg-d3rhf9odl3ps73fdh52g-a.oregon-postgres.render.com -U perekups -d perekupi
psw: jhLZjsrhsWo9nilwXAHY7bPnjYNdNN5U

# Test site link
https://perekupi-1.onrender.com/login

# Useful commands:
DROP TABLE users;
CREATE TABLE users (uid SERIAL, name VARCHAR(255), password VARCHAR(255));
INSERT INTO users (name, password, company) VALUES ('Agis', 'Password', 'Perekupi');
DELETE FROM users WHERE uid = 3;

\d users;           describes table
\dt                 describes all tables

Connect to DB via termninal:
PGPASSWORD=jhLZjsrhsWo9nilwXAHY7bPnjYNdNN5U psql -h dpg-d3rhf9odl3ps73fdh52g-a.oregon-postgres.render.com -U perekups perekupi


# Intro into technologies:
https://www.youtube.com/watch?v=RK6aAjUMcl0&list=PLg7lel5LdVjyO7jk-4biyr0fqPVygTLOk&index=1
