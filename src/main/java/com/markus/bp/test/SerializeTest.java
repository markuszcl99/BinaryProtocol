package com.markus.bp.test;

import com.alibaba.fastjson.JSON;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.util.Pool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markus.bp.domain.User;
import com.markus.bp.domain.UserProto;
import com.markus.bp.service.UserService;
import com.markus.bp.utils.ProtostuffUtils;
import com.markus.bp.utils.XmlUtils;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.nustaq.serialization.FSTConfiguration;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @author: markus
 * @date: 2022/5/11 12:18 下午
 * @Description: 序列化性能测试
 * @Blog: http://markuszhang.com/
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
public class SerializeTest {
    /**
     * 用来序列化的用户对象
     */
    @State(Scope.Benchmark)
    public static class CommonState {
        User user;
        UserProto.User userProto;

        @Setup(Level.Trial)
        public void prepare() {
            UserService userService = new UserService();
            user = userService.get();
            userProto = userService.getProto();
        }
    }


    /**
     * 测试fastjson序列化
     *
     * @param commonState
     * @return
     */
    @Benchmark
    public byte[] fastJsonSerialize(CommonState commonState) {
        return JSON.toJSONBytes(commonState.user);
    }

    /**
     * 测试jackson序列化，ObjectMapper仅初始化一个实例，不重复创建
     *
     * @param commonState
     * @param jacksonState
     * @return
     * @throws Exception
     */
    @Benchmark
    public byte[] jacksonSerialize(CommonState commonState, JacksonState jacksonState) throws Exception {
        return jacksonState.objectMapper.writeValueAsBytes(commonState.user);
    }

    @State(Scope.Benchmark)
    public static class JacksonState {
        ObjectMapper objectMapper;

        @Setup(Level.Trial)
        public void prepare() {
            objectMapper = new ObjectMapper();
        }
    }

    /**
     * 测试ObjectOutputStream序列化
     *
     * @param commonState
     * @return
     * @throws Exception
     */
    @Benchmark
    public byte[] jdkSerialize(CommonState commonState) throws Exception {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArray);
        outputStream.writeObject(commonState.user);
        outputStream.flush();
        outputStream.close();
        return byteArray.toByteArray();
    }

    /**
     * 测试kryo序列化，Kryo对象不是线程安全的，这里使用池获取
     *
     * @param commonState
     * @param kryoState
     * @return
     */
    @Benchmark
    public byte[] kryoSerialize(CommonState commonState, KryoState kryoState) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        Output output = new Output(byteArray);
        Kryo kryo = kryoState.kryoPool.obtain();
        kryo.writeClassAndObject(output, commonState.user);
        kryoState.kryoPool.free(kryo);
        output.flush();
        output.close();
        return byteArray.toByteArray();
    }

    @State(Scope.Benchmark)
    public static class KryoState {
        Pool<Kryo> kryoPool;

        @Setup(Level.Trial)
        public void prepare() {
            kryoPool = new Pool<Kryo>(true, false, 16) {
                protected Kryo create() {
                    Kryo kryo = new Kryo();
                    // 不支持循环引用
                    kryo.setReferences(false);
                    // 禁止类注册
                    kryo.setRegistrationRequired(false);
                    // kryo.register(User.class);
                    // 设置实例化器
                    kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
                    return kryo;
                }
            };
        }
    }

    /**
     * 测试fst序列化，FSTConfiguration仅初始化一个实例，不重复创建
     *
     * @param commonState
     * @param fSTConfigurationState
     * @return
     */
    @Benchmark
    public byte[] fstSerialize(CommonState commonState, FSTConfigurationState fSTConfigurationState) {
        return fSTConfigurationState.fSTConfiguration.asByteArray(commonState.user);
    }

    @State(Scope.Benchmark)
    public static class FSTConfigurationState {
        FSTConfiguration fSTConfiguration;

        @Setup(Level.Trial)
        public void prepare() {
            //fSTConfiguration = FSTConfiguration.createJsonConfiguration();
            fSTConfiguration = FSTConfiguration.createDefaultConfiguration();
        }
    }

    @Benchmark
    public byte[] protobufSerialize(CommonState commonState){
        return commonState.userProto.toByteArray();
    }

    /**
     * 测试protostuff序列化 通过自己写的工具类
     *
     * @param commonStateme
     * @return
     */
    @Benchmark
    public byte[] protostuffSerializeFromUtils(CommonState commonStateme) {
        return ProtostuffUtils.serialize(commonStateme.user);
    }

    /**
     * 测试protostuff序列化 正常测试
     *
     * @param commonStateme
     * @return
     */
    @Benchmark
    public byte[] protostuffSerialize(CommonState commonStateme) {
        Schema<User> schema = (Schema<User>)RuntimeSchema.getSchema(User.class);
        return ProtostuffIOUtil.toByteArray(commonStateme.user, schema, LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
    }

    /**
     * 测试xml序列化
     * @param commonState
     * @return
     */
    @Benchmark
    public byte[] xmlSerialize(CommonState commonState){
        return XmlUtils.serialize(commonState.user).getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializeTest.class.getSimpleName())
                .forks(1)
                .threads(1)
                .warmupIterations(10)
                .measurementIterations(10)
                .result("SerializeTest-V4.json")
                .resultFormat(ResultFormatType.JSON).build();
        new Runner(opt).run();
    }
}
