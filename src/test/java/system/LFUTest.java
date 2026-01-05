package system;

import base.TestBase;
import com.microdb.bufferpool.BufferPool;
import com.microdb.connection.Connection;
import com.microdb.model.DataBase;
import com.microdb.model.field.FieldType;
import com.microdb.model.field.IntField;
import com.microdb.model.page.Page;
import com.microdb.model.page.PageID;
import com.microdb.model.page.heap.HeapPageID;
import com.microdb.model.row.Row;
import com.microdb.model.table.DbTable;
import com.microdb.model.table.TableDesc;
import com.microdb.model.table.tablefile.HeapTableFile;
import com.microdb.transaction.Lock;
import com.microdb.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class LFUTest extends TestBase {
    public DataBase dataBase;
    private BufferPool bufferPool;

    /**
     * LFU测试模块
     * 该测试的config配置为
     * page_size = 20 一页20字节
     * evict_count = 10 LFU淘汰访问频率最低的10个页
     * buffer_pool_capacity = 15 缓存中存储15个页
     */
    @Before
    public void initDataBase() {
        DataBase dataBase = DataBase.getInstance();
        // 创建数据库文件
        String fileName = UUID.randomUUID().toString();
        List<TableDesc.Attribute> attributes = Arrays.asList(new TableDesc.Attribute("f1", FieldType.INT));
        TableDesc tableDesc = new TableDesc(attributes);
        File file = new File(fileName);
        file.deleteOnExit();
        HeapTableFile tableFile = new HeapTableFile(file, tableDesc);

        // tableDesc
        dataBase.addTable(tableFile, "t_person", tableDesc);

        this.dataBase = dataBase;
        this.bufferPool = DataBase.getBufferPool();

    }

    /**
     * t_person 表只有一个int类型字段 一行内容占据5字节（1字节的slot+4字节的int变量）
     */
    @Test
    public void insertRowTest() throws IOException {
        DbTable tablePerson = this.dataBase.getDbTableByName("t_person");
        Transaction transaction = new Transaction(Lock.LockType.XLock);
        transaction.start();
        Connection.passingTransaction(transaction);

        // 创造80行内容，每行内容5字节，一页存储20/5=4行内容，一共有20页
        for (int i = 0; i < 4*20; i++) {
            Row row = new Row(tablePerson.getTableDesc());
            row.setField(0, new IntField(i));
            //插入并不计入访问频率
            bufferPool.insertRow(row, "t_person");
        }
        ConcurrentHashMap<PageID, Page> pool = bufferPool.getPool();
        //产生了20页，根据插入的先后顺序验证第19~5号页现在在内存中。
        Set<Integer> set =new TreeSet<>();
        Set<Integer> set2 =new TreeSet<>();
        for (int i = 19; i >= 5; i--) {
            set.add(i);
        }
        for (Page page : pool.values()) {
            set2.add(page.getPageID().getPageNo());
        }
        Assert.assertEquals(set, set2);
        //手动通过bufferPool进行访问，19号页访问0次，18号页访问1次...，0号页访问19次
        for(int j=19; j>=0; j--) {
            for (int i = 0; i < 19-j; i++) {
                PageID pageID = new HeapPageID(tablePerson.getTableId(), j);
                bufferPool.getPage(pageID);
            }
        }
        //此时bufferPool中应该是访问频率最高的10个页（访问4号页时将19-10号页淘汰出缓存，因此剩余15-10+5=10个页面），0-9号页，访问频率从19-10次
        pool = bufferPool.getPool();
        Set<Page> set3 =new TreeSet<>(new Comparator<Page>() {
            @Override
            public int compare(Page o1, Page o2) {
                return Integer.compare(o1.getPageID().getPageNo(), o2.getPageID().getPageNo());
            }
        });
        set3.addAll(pool.values());int i=0;
        for (Page page : set3) {
            Assert.assertEquals(page.getPageID().getPageNo(), i);
            Assert.assertEquals(page.getAccessFrequency().intValue(), 19-i);
            i++;
        }
        transaction.commit();

    }


}
