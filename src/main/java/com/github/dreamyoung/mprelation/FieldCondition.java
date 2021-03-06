package com.github.dreamyoung.mprelation;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.ObjectFactory;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

public class FieldCondition<T>{
	private ObjectFactory<SqlSession> factory;

	enum FieldCollectionType {
		LIST, SET, NONE
	};

	private T entity;
	private Field field;
	private Boolean fetchEager;

	private String name;
	private Boolean isCollection;
	private FieldCollectionType fieldCollectionType;
	private RelationType relationType;
	private Class<?> fieldClass;
	private Boolean isLazy;

	private TableId tableId;
	private Field fieldOfTableId;
	private TableField tableField;

	private TableId refTableId;
	private Field fieldOfRefTableId;
	private TableField refTableField;

	private TableId inverseTableId;
	private Field fieldOfInverseTableId;
	private TableField inverseTableField;

	private OneToMany oneToMany;
	private OneToOne oneToOne;
	private ManyToOne manyToOne;
	private ManyToMany manyToMany;
	private FetchType fetchType;
	private Lazy lazy;
	private JoinColumn joinColumn;
	private JoinColumns joinColumns;
	private JoinTable joinTable;
	private InverseJoinColumn inverseJoinColumn;
	private EntityMapper entityMapper;
	private Class<?> mapperClass;
	private Class<?> joinTableMapperClass;

	public FieldCondition(T entity, Field field, boolean fetchEager, ObjectFactory<SqlSession> factory) {
		this.factory = factory;

		this.entity = entity;
		this.field = field;
		this.field.setAccessible(true);
		this.fetchEager = fetchEager;

		this.name = field.getName();
		this.isCollection = field.getType() == List.class || field.getType() == ArrayList.class
				|| field.getType() == Set.class || field.getType() == HashSet.class;
		this.fieldClass = field.getType();
		if (isCollection) {
			Type genericType = field.getGenericType();
			if (genericType != null && genericType instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) genericType;
				this.fieldClass = (Class<?>) pt.getActualTypeArguments()[0];
			}
		}

		if (field.getType() == List.class || field.getType() == ArrayList.class) {
			this.fieldCollectionType = FieldCollectionType.LIST;
		} else if (field.getType() == Set.class || field.getType() == HashSet.class) {
			this.fieldCollectionType = FieldCollectionType.SET;
		} else {
			this.fieldCollectionType = FieldCollectionType.NONE;
		}

		this.tableField = field.getAnnotation(TableField.class);
		this.oneToMany = field.getAnnotation(OneToMany.class);
		this.oneToOne = field.getAnnotation(OneToOne.class);
		this.manyToOne = field.getAnnotation(ManyToOne.class);
		this.manyToMany = field.getAnnotation(ManyToMany.class);
		if (oneToMany != null) {
			this.relationType = RelationType.ONETOMANY;
			this.fetchType = oneToMany.fetch();
		} else if (oneToOne != null) {
			this.relationType = RelationType.ONETOONE;
			this.fetchType = oneToOne.fetch();
		} else if (manyToOne != null) {
			this.relationType = RelationType.MANYTOONE;
			this.fetchType = manyToOne.fetch();
		} else if (manyToMany != null) {
			this.relationType = RelationType.MANYTOMANY;
			this.fetchType = manyToMany.fetch();
		}

		this.lazy = field.getAnnotation(Lazy.class);
		this.joinColumn = field.getAnnotation(JoinColumn.class);
		this.joinColumns = field.getAnnotation(JoinColumns.class);
		this.joinTable = field.getAnnotation(JoinTable.class);
		this.inverseJoinColumn = field.getAnnotation(InverseJoinColumn.class);
		this.entityMapper = field.getAnnotation(EntityMapper.class);

		if (fetchEager == true) {
			isLazy = false;
		} else {
			if (lazy != null) {
				isLazy = lazy.value();
			} else {
				isLazy = fetchType == FetchType.LAZY;
			}
		}

		TableIdCondition tidCondition = new TableIdCondition(entity.getClass());
		this.tableId = tidCondition.getTableId();
		this.fieldOfTableId = tidCondition.getFieldOfTableId();

		if (inverseJoinColumn != null) {
			TableIdCondition tidConditionInverse = new TableIdCondition(fieldClass);
			this.inverseTableId = tidConditionInverse.getTableId();
			this.fieldOfInverseTableId = tidConditionInverse.getFieldOfTableId();
		}

		if (!isCollection) {
			TableIdCondition tidConditionRef = new TableIdCondition(fieldClass);
			this.refTableId = tidConditionRef.getTableId();
			this.fieldOfRefTableId = tidConditionRef.getFieldOfTableId();
		}
		

		this.mapperClass = null;
		if (entityMapper != null && entityMapper.targetMapper() != void.class) {
			mapperClass = entityMapper.targetMapper();
		} else {
			String entityName = this.getFieldClass().getSimpleName();
			Collection<Class<?>> mappers = this.factory.getObject().getConfiguration().getMapperRegistry().getMappers();
			for (Class<?> mapperClz : mappers) {
				String mapperClassName = mapperClz.getSimpleName();
				if (mapperClassName.equalsIgnoreCase(entityName + "Mapper")) {
					mapperClass = mapperClz;
					break;
				}
			}

			if (mapperClass == null) {
				throw new RelationException(
						"[Class: FieldCondition=>FieldCondition(T entity, Field field, boolean fetchEager, ObjectFactory<SqlSession> factory)],RelationException By: load Class(Mapper Interface):"
								+ this.getFieldClass().getSimpleName() + "Mapper");
			}
		}

		this.joinTableMapperClass = null;
		String[] joinMapperNames = new String[] {
				entity.getClass().getSimpleName() + this.getFieldClass().getSimpleName() + "Mapper",
				this.getFieldClass().getSimpleName() + entity.getClass().getSimpleName() + "Mapper" };

		if (joinTable != null) {
			if (joinTable.targetMapper() != null && joinTable.targetMapper() != void.class) {
				joinTableMapperClass = joinTable.targetMapper();
			} else {
				Collection<Class<?>> mappers = this.factory.getObject().getConfiguration().getMapperRegistry()
						.getMappers();
				boolean isMapperFound = false;
				for (String joinMapperName : joinMapperNames) {
					if (isMapperFound) {
						break;
					}

					for (Class<?> mapperClz : mappers) {
						if (mapperClz.getSimpleName().equalsIgnoreCase(joinMapperName)) {
							isMapperFound = true;
							joinTableMapperClass = mapperClz;
							break;
						}
					}
				}

				if (!isMapperFound) {
					throw new RelationException(
							"[Class: FieldCondition=>FieldCondition(T entity, Field field, boolean fetchEager, ObjectFactory<SqlSession> factory)],RelationException By: load Class(Mapper Interface):"
									+ entity.getClass().getSimpleName() + this.getFieldClass().getSimpleName()
									+ "Mapper" + " Or " + this.getFieldClass().getSimpleName()
									+ entity.getClass().getSimpleName() + "Mapper");
				}

			}
		}

	}

	public Field getField() {
		return field;
	}

	public void setField(Field field) {
		this.field = field;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getIsCollection() {
		return isCollection;
	}

	public void setIsCollection(Boolean isCollection) {
		this.isCollection = isCollection;
	}

	public Class<?> getFieldClass() {
		return fieldClass;
	}

	public void setFieldClass(Class<?> fieldClass) {
		this.fieldClass = fieldClass;
	}

	public TableField getTableField() {
		return tableField;
	}

	public void setTableField(TableField tableField) {
		this.tableField = tableField;
	}

	public OneToMany getOneToMany() {
		return oneToMany;
	}

	public void setOneToMany(OneToMany oneToMany) {
		this.oneToMany = oneToMany;
	}

	public Lazy getLazy() {
		return lazy;
	}

	public void setLazy(Lazy lazy) {
		this.lazy = lazy;
	}

	public JoinColumn getJoinColumn() {
		return joinColumn;
	}

	public void setJoinColumn(JoinColumn joinColumn) {
		this.joinColumn = joinColumn;
	}

	public EntityMapper getEntityMapper() {
		return entityMapper;
	}

	public void setEntityMapper(EntityMapper entityMapper) {
		this.entityMapper = entityMapper;
	}

	public OneToOne getOneToOne() {
		return oneToOne;
	}

	public void setOneToOne(OneToOne oneToOne) {
		this.oneToOne = oneToOne;
	}

	public ManyToOne getManyToOne() {
		return manyToOne;
	}

	public void setManyToOne(ManyToOne manyToOne) {
		this.manyToOne = manyToOne;
	}

	public ManyToMany getManyToMany() {
		return manyToMany;
	}

	public void setManyToMany(ManyToMany manyToMany) {
		this.manyToMany = manyToMany;
	}

	public JoinColumns getJoinColumns() {
		return joinColumns;
	}

	public void setJoinColumns(JoinColumns joinColumns) {
		this.joinColumns = joinColumns;
	}

	public Boolean getIsLazy() {
		return isLazy;
	}

	public void setIsLazy(Boolean isLazy) {
		this.isLazy = isLazy;
	}

	public FieldCollectionType getFieldCollectionType() {
		return fieldCollectionType;
	}

	public void setFieldCollectionType(FieldCollectionType fieldCollectionType) {
		this.fieldCollectionType = fieldCollectionType;
	}

	public JoinTable getJoinTable() {
		return joinTable;
	}

	public void setJoinTable(JoinTable joinTable) {
		this.joinTable = joinTable;
	}


	@Override
	public String toString() {
		return "FieldCondition [factory=" + factory + ", entity=" + entity + ", field=" + field + ", fetchEager="
				+ fetchEager + ", name=" + name + ", isCollection=" + isCollection + ", fieldCollectionType="
				+ fieldCollectionType + ", relationType=" + relationType + ", fieldClass=" + fieldClass + ", isLazy="
				+ isLazy + ", tableId=" + tableId + ", fieldOfTableId=" + fieldOfTableId + ", tableField=" + tableField
				+ ", refTableId=" + refTableId + ", fieldOfRefTableId=" + fieldOfRefTableId + ", refTableField="
				+ refTableField + ", inverseTableId=" + inverseTableId + ", fieldOfInverseTableId="
				+ fieldOfInverseTableId + ", inverseTableField=" + inverseTableField + ", oneToMany=" + oneToMany
				+ ", oneToOne=" + oneToOne + ", manyToOne=" + manyToOne + ", manyToMany=" + manyToMany + ", fetchType="
				+ fetchType + ", lazy=" + lazy + ", joinColumn=" + joinColumn + ", joinColumns=" + joinColumns
				+ ", joinTable=" + joinTable + ", inverseJoinColumn=" + inverseJoinColumn + ", entityMapper="
				+ entityMapper + ", mapperClass=" + mapperClass + ", joinTableMapperClass=" + joinTableMapperClass
				+ "]";
	}

	public TableId getTableId() {
		return tableId;
	}

	public void setTableId(TableId tableId) {
		this.tableId = tableId;
	}

	public Field getFieldOfTableId() {
		return fieldOfTableId;
	}

	public void setFieldOfTableId(Field fieldOfTableId) {
		this.fieldOfTableId = fieldOfTableId;
	}

	public T getEntity() {
		return entity;
	}

	public void setEntity(T entity) {
		this.entity = entity;
	}

	public InverseJoinColumn getInverseJoinColumn() {
		return inverseJoinColumn;
	}

	public void setInverseJoinColumn(InverseJoinColumn inverseJoinColumn) {
		this.inverseJoinColumn = inverseJoinColumn;
	}

	public Class<?> getMapperClass() {
		return mapperClass;
	}

	public void setMapperClass(Class<?> mapperClass) {
		this.mapperClass = mapperClass;
	}

	public <E> void setFieldValueByList(List<E> list) {
		if (list != null) {
			field.setAccessible(true);
			try {
				if (this.getFieldCollectionType() == FieldCollectionType.SET) {
					// list to set
					Set<E> set = new HashSet<E>();
					for (E e : list) {// list 被访问，导致延迟立即加载，延迟失败！
						set.add(e);
					}
					field.set(entity, set);

				} else {
					field.set(entity, list);
				}

			} catch (Exception e) {
				throw new OneToManyException(
						String.format("{0} call setter {1} is not correct!", entity, field.getName()));
			}
		}
	}

	public <E> void setFieldValueBySet(Set<E> set) {
		if (set != null) {
			field.setAccessible(true);
			try {
				field.set(entity, set);
			} catch (Exception e) {
				throw new OneToManyException(
						String.format("{0} call setter {1} is not correct!", entity, field.getName()));
			}
		}
	}

	public <E> void setFieldValueByObject(E e) {
		field.setAccessible(true);
		try {
			field.set(entity, e);
		} catch (Exception ex) {
			throw new OneToOneException(String.format("{0} call setter {1} is not correct!", entity, field.getName()));
		}
	}

	public Class<?> getJoinTableMapperClass() {
		return joinTableMapperClass;
	}

	public void setJoinTableMapperClass(Class<?> joinTableMapperClass) {
		this.joinTableMapperClass = joinTableMapperClass;
	}

	public Boolean getFetchEager() {
		return fetchEager;
	}

	public void setFetchEager(Boolean fetchEager) {
		this.fetchEager = fetchEager;
	}

	public RelationType getRelationType() {
		return relationType;
	}

	public void setRelationType(RelationType relationType) {
		this.relationType = relationType;
	}

	public FetchType getFetchType() {
		return fetchType;
	}

	public void setFetchType(FetchType fetchType) {
		this.fetchType = fetchType;
	}

	public TableId getInverseTableId() {
		return inverseTableId;
	}

	public void setInverseTableId(TableId inverseTableId) {
		this.inverseTableId = inverseTableId;
	}

	public Field getFieldOfInverseTableId() {
		return fieldOfInverseTableId;
	}

	public void setFieldOfInverseTableId(Field fieldOfInverseTableId) {
		this.fieldOfInverseTableId = fieldOfInverseTableId;
	}

	public TableField getInverseTableField() {
		return inverseTableField;
	}

	public void setInverseTableField(TableField inverseTableField) {
		this.inverseTableField = inverseTableField;
	}

	public TableId getRefTableId() {
		return refTableId;
	}

	public void setRefTableId(TableId refTableId) {
		this.refTableId = refTableId;
	}

	public Field getFieldOfRefTableId() {
		return fieldOfRefTableId;
	}

	public void setFieldOfRefTableId(Field fieldOfRefTableId) {
		this.fieldOfRefTableId = fieldOfRefTableId;
	}

	public TableField getRefTableField() {
		return refTableField;
	}

	public void setRefTableField(TableField refTableField) {
		this.refTableField = refTableField;
	}

}
