package ua.quietlycore.scan.fixtures.allowed.deep;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@FilterDef(name = "obj.category", parameters = @ParamDef(name = "category", type = String.class))
@Filter(name = "obj.category", condition = "category = :category")
public class NestedFilteredEntity
{

   public String category;
}
