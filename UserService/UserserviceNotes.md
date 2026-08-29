

1.so From Engineers prespective when we are assigning them to a supervisor :
A> then we apply @Manytoone Mapping   
@Manytoone: 
1.is for child table (owner of relationship) as because of this foreignKey is created 
also because of this ,a new foreignkey column is created that is referencing the parent table primary key in db.
2.so using this  we can know who is the parent
in entity: private PARENT_TABLE_NAME parent_user; is  for navigation purpose(navigate foreign key to parent table).


2 so when we want our Supervisor know what are the Enginners under him Then
A> then we apply @OnetoMany Mapping
@onetoMany: is for the parent table
It is only when the parent want to know who is there childrens.(means only for navigation purpose)
here now not any column is made
in Entity: only private list<chldTablename> child_user ; is used  only for navigation purpose

As  our user Table is same for supervisor/engineer so for assigning the enginners under the supervisor so we are Applying  Self Referencing Relationship (or Recursive Relationship) in our table
means Both @Manytoone  and @OnetoMany in same Entity

Eg:
User Table Structure

userid (PK)| name | role        | supervisor_id (Fk)
---------------------------------------
1  | A    | SUPERVISOR  | null
2  | B    | ENGINEER    | 1
3  | C    | ENGINEER    | 1

🔑 Important Points
•	supervisor_id is a foreign key referencing the same table primary key which userId
•	No extra table is required
•	No join table is required

In our Entity:
Many engineers → one supervisor (OWNER SIDE/child side)
    @ManyToOne
    @JoinColumn(name = "supervisor_id")
    private User supervisor;     // is for navigation of foreignkey

One supervisor → many engineers (INVERSE SIDE)
    @OneToMany(mappedBy = "supervisor")
    private List<User> engineers; //is for navigation purpose 

