package vn.offline.sokhachcuoi.v2;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class DbHelper extends SQLiteOpenHelper {
    public DbHelper(Context c){ super(c,"wedding_guests_v2.db",null,2); }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL)");
        db.execSQL("CREATE TABLE guests(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,group_name TEXT,phone TEXT,called INTEGER DEFAULT 0,invited INTEGER DEFAULT 0,guest_count INTEGER DEFAULT 1,gift INTEGER,note TEXT,created_at INTEGER)");
        String[] g={"Cơ quan","Bạn bè","Họ hàng","Hàng xóm","Khách của bố mẹ","Khác"};
        for(String s:g){ ContentValues v=new ContentValues();v.put("name",s);db.insert("groups",null,v); }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b){ if(a<2){try{db.execSQL("ALTER TABLE guests ADD COLUMN guest_count INTEGER DEFAULT 1");}catch(Exception ignored){} try{db.execSQL("ALTER TABLE guests ADD COLUMN note TEXT");}catch(Exception ignored){}} }
    public long saveGuest(Long id,String name,String group,String phone,boolean called,boolean invited,Long gift){
        ContentValues v=new ContentValues(); v.put("name",name.trim());v.put("group_name",group);v.put("phone",phone.trim());v.put("called",called?1:0);v.put("invited",invited?1:0);
        if(gift==null)v.putNull("gift");else v.put("gift",gift);v.put("created_at",System.currentTimeMillis());
        if(id==null)return getWritableDatabase().insert("guests",null,v);
        getWritableDatabase().update("guests",v,"id=?",new String[]{String.valueOf(id)});return id;
    }

    public long saveGuestV2(String name,String group,String phone,boolean called,boolean invited,int guestCount,Long gift,String note){
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("group_name",group);v.put("phone",phone==null?"":phone.trim());v.put("called",called?1:0);v.put("invited",invited?1:0);v.put("guest_count",Math.max(1,guestCount));if(gift==null)v.putNull("gift");else v.put("gift",gift);v.put("note",note==null?"":note);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("guests",null,v);
    }
    public void toggle(long id,String field,boolean value){ ContentValues v=new ContentValues();v.put(field,value?1:0);getWritableDatabase().update("guests",v,"id=?",new String[]{String.valueOf(id)}); }
    public void deleteGuest(long id){getWritableDatabase().delete("guests","id=?",new String[]{String.valueOf(id)});}
    public Cursor guests(String q,String group,String state){
        ArrayList<String> where=new ArrayList<>(),args=new ArrayList<>();
        if(q!=null&&!q.trim().isEmpty()){where.add("(name LIKE ? OR phone LIKE ?)");args.add("%"+q.trim()+"%");args.add("%"+q.trim()+"%");}
        if(group!=null&&!group.equals("Tất cả nhóm")){where.add("group_name=?");args.add(group);}
        if("Chưa mời ĐT".equals(state))where.add("called=0"); else if("Chưa gửi thiệp".equals(state))where.add("invited=0"); else if("Đã mời ĐT".equals(state))where.add("called=1"); else if("Đã gửi thiệp".equals(state))where.add("invited=1");
        String w=where.isEmpty()?"":" WHERE "+String.join(" AND ",where);
        return getReadableDatabase().rawQuery("SELECT * FROM guests"+w+" ORDER BY id",args.toArray(new String[0]));
    }
    public ArrayList<String> groups(){ArrayList<String>a=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT name FROM groups ORDER BY id",null);while(c.moveToNext())a.add(c.getString(0));c.close();return a;}
    public void addGroup(String s){if(s==null||s.trim().isEmpty())return;ContentValues v=new ContentValues();v.put("name",s.trim());getWritableDatabase().insertWithOnConflict("groups",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public void deleteGroup(String s){getWritableDatabase().delete("groups","name=?",new String[]{s});}
    public long count(String cond){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM guests"+(cond==null?"":" WHERE "+cond),null);c.moveToFirst();long n=c.getLong(0);c.close();return n;}
    public long giftTotal(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(gift),0) FROM guests",null);c.moveToFirst();long n=c.getLong(0);c.close();return n;}
    public JSONObject exportJson() throws Exception{
        JSONObject root=new JSONObject();root.put("version",1);JSONArray gs=new JSONArray();Cursor c=getReadableDatabase().rawQuery("SELECT name FROM groups ORDER BY id",null);while(c.moveToNext())gs.put(c.getString(0));c.close();root.put("groups",gs);
        JSONArray arr=new JSONArray();c=getReadableDatabase().rawQuery("SELECT name,group_name,phone,called,invited,gift,created_at FROM guests ORDER BY id",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("name",c.getString(0));o.put("group",c.getString(1));o.put("phone",c.getString(2));o.put("called",c.getInt(3));o.put("invited",c.getInt(4));if(!c.isNull(5))o.put("gift",c.getLong(5));o.put("created",c.getLong(6));arr.put(o);}c.close();root.put("guests",arr);return root;
    }
    public void importJson(JSONObject root)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("guests",null,null);db.delete("groups",null,null);JSONArray gs=root.getJSONArray("groups");for(int i=0;i<gs.length();i++)addGroup(gs.getString(i));JSONArray a=root.getJSONArray("guests");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);saveGuest(null,o.getString("name"),o.optString("group",""),o.optString("phone",""),o.optInt("called")==1,o.optInt("invited")==1,o.has("gift")?o.getLong("gift"):null);}db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
}
