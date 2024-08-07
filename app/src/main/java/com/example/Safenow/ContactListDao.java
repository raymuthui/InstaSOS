package com.example.Safenow;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ContactListDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ContactList... contacts);

    @Update
    void update(ContactList contacts);

    @Delete
    void delete(ContactList contacts);

    @Query("DELETE FROM ContactList")
    void deleteAllContacts();

    @Query("SELECT * FROM ContactList ORDER BY firstName ASC")
    LiveData<List<ContactList>> getAlphabetizedContacts();

    @Query("UPDATE ContactList SET isDefault = 1 WHERE id = :id")
    void setDefaultContact(int id);

    @Query("UPDATE ContactList SET isDefault = 0 WHERE id = :id")
    void unsetDefaultContact(int id);

    @Query("UPDATE ContactList SET isDefault = 0 WHERE isDefault = 1")
    void unsetAllDefaultContacts();

}
